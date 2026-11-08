package com.example.quickvote.invite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.Db;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.Tokens;
import com.example.quickvote.event.PollEventService;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.poll.JoinResponse;
import com.example.quickvote.poll.Poll;
import com.example.quickvote.poll.PollRepository;
import com.example.quickvote.poll.PollRules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

    static final Duration VALIDITY = Duration.ofHours(24);

    private final ParticipantService access;
    private final InviteRepository invites;
    private final PollRepository polls;
    private final ParticipantRepository participants;
    private final PollEventService events;
    private final Clock clock;

    public InviteService(ParticipantService access, InviteRepository invites, PollRepository polls,
            ParticipantRepository participants, PollEventService events, Clock clock) {
        this.access = access;
        this.invites = invites;
        this.polls = polls;
        this.participants = participants;
        this.events = events;
        this.clock = clock;
    }

    /** 共同主催者の招待を発行する（HOSTのみ）。トークンは応答でだけ返し、DB にはハッシュを保存する。 */
    @Transactional
    public InviteResponse issue(long visitorId, UUID pollPublicId) {
        ParticipantContext context = access.requireHost(pollPublicId, visitorId);
        Instant now = Db.now(clock);
        Instant expiresAt = now.plus(VALIDITY);
        String token = Tokens.newToken();
        invites.insert(context.pollId(), Tokens.sha256Hex(token), now, expiresAt);
        return new InviteResponse(token, expiresAt);
    }

    /**
     * 招待を受ける。受けた人は HOST になる（すでに MEMBER なら昇格）。
     * <p>
     * 順序: ① トークンから投票を特定 → ② polls 行をロック（人数上限・締切の判定のため。join と同じ規則）
     * → ③ 招待を原子的に消費 → ④ 締切後なら拒否 → ⑤ 参加者を作る/昇格。
     * 締切後の投票には、招待でも参加者を増やさない（参加コードでの参加と同じ。投票が確定した後に顔ぶれを変えない）。
     * 締切の判定は、ロックを取った後（ロックの前だと、判定してから締め切られる隙間ができる）。
     * ④⑤で失敗すれば例外でトランザクション全体がロールバックされ、③の消費も取り消される
     * （招待は使われないまま残る）。
     */
    @Transactional
    public JoinResponse accept(long visitorId, String token) {
        String tokenHash = Tokens.sha256Hex(token);
        long pollId = invites.findPollId(tokenHash).orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
        Poll poll = polls.lockById(pollId).orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));

        Instant now = Db.now(clock);
        if (invites.consume(tokenHash, visitorId, now) == 0) {
            // 使用済み・期限切れ・（直前に他の人が消費した）のどれでも同じ応答。理由を区別して教えない。
            throw new ApiException(ErrorCode.INVITE_INVALID);
        }
        if (poll.isClosedAt(now)) {
            // 有効な招待でも、締切後は受けられない。例外でロールバックされるので、上の消費も取り消される。
            // 無効な招待（上の分岐）より後に判定するのは、無効な招待に「投票が締め切られているか」を教えないため。
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }

        Optional<ParticipantRepository.Participant> existing = participants.findByPollAndVisitor(pollId, visitorId);
        if (existing.isPresent()) {
            if (existing.get().role() != ParticipantRole.HOST) {
                participants.promoteToHost(existing.get().id());
            }
        } else {
            int current = participants.countActive(pollId);
            if (current >= PollRules.MAX_PARTICIPANTS) {
                throw new ApiException(ErrorCode.POLL_FULL);
            }
            participants.insert(pollId, visitorId, ParticipantRole.HOST, now);
            events.append(pollId, "PARTICIPANT_JOINED", Map.of("participantCount", current + 1));
        }
        return new JoinResponse(poll.publicId(), ParticipantRole.HOST);
    }
}
