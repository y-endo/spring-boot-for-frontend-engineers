package com.example.jpalab.poll;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * votes テーブルの1行。QuickVote の votes は主キーを持たない（UNIQUE だけ）が、JPA は @Id が必須なので代理キー id を足している。
 * poll_id は複合外部キー (poll_id, option_id) の片割れ。JPA では「ただの列」として持つしかない。
 */
@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id")
    private Option option;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    protected Vote() {
    }

    public Vote(Long pollId, Long participantId, Option option, Instant votedAt) {
        this.pollId = pollId;
        this.participantId = participantId;
        this.option = option;
        this.votedAt = votedAt;
    }

    public void changeOption(Option newOption, Instant now) {
        this.option = newOption;
        this.votedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getPollId() {
        return pollId;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public Option getOption() {
        return option;
    }

    public Instant getVotedAt() {
        return votedAt;
    }
}
