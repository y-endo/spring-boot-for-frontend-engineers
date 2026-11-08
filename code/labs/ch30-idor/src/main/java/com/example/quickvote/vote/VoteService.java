package com.example.quickvote.vote;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.poll.OptionRepository;
import com.example.quickvote.poll.Poll;
import com.example.quickvote.poll.PollRepository;

import org.springframework.stereotype.Service;

@Service
public class VoteService {

    private final ParticipantService access;
    private final PollRepository polls;
    private final OptionRepository options;
    private final VoteRepository votes;
    private final Clock clock;

    public VoteService(ParticipantService access, PollRepository polls, OptionRepository options,
            VoteRepository votes, Clock clock) {
        this.access = access;
        this.polls = polls;
        this.options = options;
        this.votes = votes;
        this.clock = clock;
    }

    /**
     * 投票（上書き）。PUT なので、同じ内容を何度送っても結果は同じ（本質的に冪等）。
     * <p>
     * 1人1票は、VoteRepository のキー（poll_id, participant_id）が守る。
     * この時点の保証: 締切の判定と票の書き込みの間は守られていない（第4部で、ロックと合わせて直す）。
     */
    public VoteResponse vote(long visitorId, UUID pollPublicId, long optionId) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        Poll poll = polls.findById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        Instant now = clock.instant();
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        // 他の投票の選択肢IDを指定されても「この投票の選択肢ではない」として404にする。
        if (!options.existsInPoll(poll.id(), optionId)) {
            throw new ApiException(ErrorCode.OPTION_NOT_FOUND);
        }
        votes.upsert(poll.id(), context.participantId(), optionId, now);
        return new VoteResponse(optionId);
    }
}
