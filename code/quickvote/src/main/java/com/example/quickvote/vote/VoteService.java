package com.example.quickvote.vote;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.Db;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.event.PollEventService;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.poll.OptionRepository;
import com.example.quickvote.poll.Poll;
import com.example.quickvote.poll.PollRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {

    private final ParticipantService access;
    private final PollRepository polls;
    private final OptionRepository options;
    private final VoteRepository votes;
    private final PollEventService events;
    private final Clock clock;

    public VoteService(ParticipantService access, PollRepository polls, OptionRepository options,
            VoteRepository votes, PollEventService events, Clock clock) {
        this.access = access;
        this.polls = polls;
        this.options = options;
        this.votes = votes;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 投票（上書き）。PUT なので、同じ内容を何度送っても結果は同じ（本質的に冪等）。
     * <p>
     * polls 行をロックする理由は2つ: ① 締切・手動締切との競合（締切判定と票の書き込みの間に割り込ませない）
     * ② イベントの採番（連番）を投票ごとに直列化する。
     * 1人1票そのものは、ロックではなく UNIQUE(poll_id, participant_id) ＋ ON CONFLICT が守る。
     * ロックを忘れた更新経路があっても、票が二重になることはない。
     */
    @Transactional
    public VoteResponse vote(long visitorId, UUID pollPublicId, long optionId) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        Poll poll = polls.lockById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        Instant now = Db.now(clock);
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        // 他の投票の選択肢IDを指定されても「この投票の選択肢ではない」として404にする。
        if (!options.existsInPoll(poll.id(), optionId)) {
            throw new ApiException(ErrorCode.OPTION_NOT_FOUND);
        }
        boolean changed = votes.upsert(poll.id(), context.participantId(), optionId, now) > 0;
        if (changed) {
            events.append(poll.id(), "VOTE_CAST", Map.of("optionId", optionId));
        }
        return new VoteResponse(optionId);
    }
}
