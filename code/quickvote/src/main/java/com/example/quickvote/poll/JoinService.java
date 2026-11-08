package com.example.quickvote.poll;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.Db;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.event.PollEventService;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JoinService {

    private final PollRepository polls;
    private final ParticipantRepository participants;
    private final PollEventService events;
    private final Clock clock;

    public JoinService(PollRepository polls, ParticipantRepository participants, PollEventService events,
            Clock clock) {
        this.polls = polls;
        this.participants = participants;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 参加コードで参加する。人数上限(50)は「polls 行をロック → 再カウント → 挿入」で守る。
     * 同じ訪問者の再参加は成功として扱い、何も変えない（再送に強い）。
     */
    @Transactional
    public JoinResponse join(long visitorId, String rawCode) {
        String code = JoinCodeGenerator.normalize(rawCode);
        if (!JoinCodeGenerator.isWellFormed(code)) {
            throw new ApiException(ErrorCode.JOIN_CODE_NOT_FOUND);
        }
        Poll poll = polls.lockByJoinCode(code).orElseThrow(() -> new ApiException(ErrorCode.JOIN_CODE_NOT_FOUND));

        Optional<ParticipantRepository.Participant> existing = participants.findByPollAndVisitor(poll.id(), visitorId);
        if (existing.isPresent()) {
            return new JoinResponse(poll.publicId(), existing.get().role());
        }
        Instant now = Db.now(clock);
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        int current = participants.countActive(poll.id());
        if (current >= PollRules.MAX_PARTICIPANTS) {
            throw new ApiException(ErrorCode.POLL_FULL);
        }
        participants.insert(poll.id(), visitorId, ParticipantRole.MEMBER, now);
        events.append(poll.id(), "PARTICIPANT_JOINED", Map.of("participantCount", current + 1));
        return new JoinResponse(poll.publicId(), ParticipantRole.MEMBER);
    }
}
