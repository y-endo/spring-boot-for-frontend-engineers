package com.example.jpalab.poll;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** QuickVote の VoteService.vote を、JPA で素直に書き直したもの（第41章の比較用）。 */
@Service
public class VoteService {

    private static final Logger lab = LoggerFactory.getLogger("lab");

    private final PollRepository polls;
    private final OptionRepository options;
    private final VoteRepository votes;

    public VoteService(PollRepository polls, OptionRepository options, VoteRepository votes) {
        this.polls = polls;
        this.options = options;
        this.votes = votes;
    }

    /** 素直な JPA 版: 探す → なければ INSERT、あれば Entity を書き換える（UPDATE は dirty checking）。 */
    @Transactional
    public void voteWithJpa(Long pollId, Long participantId, Long optionId, Instant now) {
        Option option = options.getReferenceById(optionId);
        Vote vote = votes.findByPollIdAndParticipantId(pollId, participantId).orElse(null);
        if (vote == null) {
            votes.save(new Vote(pollId, participantId, option, now));
        } else {
            vote.changeOption(option, now);
        }
    }

    /** QuickVote と同じ ON CONFLICT の SQL を、ネイティブクエリで呼ぶ版。 */
    @Transactional
    public int voteWithNativeUpsert(Long pollId, Long participantId, Long optionId, Instant now) {
        return votes.upsert(pollId, participantId, optionId, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    /** QuickVote の PollService.addOption / VoteService.vote が最初にやる「親行ロック」の JPA 版。 */
    @Transactional
    public Poll lockPoll(Long pollId) {
        lab.info("-- 親行ロック");
        return polls.lockById(pollId).orElseThrow();
    }
}
