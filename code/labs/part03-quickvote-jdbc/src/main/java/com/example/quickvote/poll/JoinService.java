package com.example.quickvote.poll;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.Db;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class JoinService {

    private final PollRepository polls;
    private final ParticipantRepository participants;
    private final Clock clock;

    public JoinService(PollRepository polls, ParticipantRepository participants, Clock clock) {
        this.polls = polls;
        this.participants = participants;
        this.clock = clock;
    }

    /**
     * 参加コードで参加する。同じ訪問者の再参加は成功として扱い、何も変えない（再送に強い）。
     * <p>
     * この時点の保証: 人数上限(50)の判定は「数える → 足す」の2操作で、その間は守られていない。
     * 49人のときに2人が同時に参加すると51人になりうる（第4部で、DBのロックで直す）。
     */
    public JoinResponse join(long visitorId, String rawCode) {
        String code = JoinCodeGenerator.normalize(rawCode);
        if (!JoinCodeGenerator.isWellFormed(code)) {
            throw new ApiException(ErrorCode.JOIN_CODE_NOT_FOUND);
        }
        Poll poll = polls.findByJoinCode(code).orElseThrow(() -> new ApiException(ErrorCode.JOIN_CODE_NOT_FOUND));

        Optional<ParticipantRepository.Participant> existing = participants.findByPollAndVisitor(poll.id(), visitorId);
        if (existing.isPresent()) {
            return new JoinResponse(poll.publicId(), existing.get().role());
        }
        Instant now = Db.now(clock);
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        if (participants.countActive(poll.id()) >= PollRules.MAX_PARTICIPANTS) {
            throw new ApiException(ErrorCode.POLL_FULL);
        }
        try {
            participants.insert(poll.id(), visitorId, ParticipantRole.MEMBER, now);
        } catch (DuplicateKeyException e) {
            // 同じ人が「確認」と「挿入」の間にもう一度参加した（ボタンの連打）。UNIQUE(poll_id, visitor_id) が止めた。
            // 再参加と同じ扱い（成功。何も変えない）に翻訳する。
            ParticipantRole role = participants.findByPollAndVisitor(poll.id(), visitorId).orElseThrow().role();
            return new JoinResponse(poll.publicId(), role);
        }
        return new JoinResponse(poll.publicId(), ParticipantRole.MEMBER);
    }
}
