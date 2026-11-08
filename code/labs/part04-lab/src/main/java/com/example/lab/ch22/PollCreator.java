package com.example.lab.ch22;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「投票を作る」= polls に1行 + participants に主催者(HOST)を1行。2本の INSERT で1つの業務操作になる。
 * 2本のあいだに failBetween で例外を起こせる。違いは @Transactional の有無だけ。
 */
@Service
public class PollCreator {

    private final LabRepository repo;

    public PollCreator(LabRepository repo) {
        this.repo = repo;
    }

    public long createWithoutTx(String title, String joinCode, long hostVisitorId, boolean failBetween) {
        long pollId = repo.insertPoll(title, joinCode);              // 1本目のINSERT
        if (failBetween) {
            throw new IllegalStateException("2本のINSERTの間で失敗した");
        }
        repo.insertParticipant(pollId, hostVisitorId, "HOST");       // 2本目のINSERT
        return pollId;
    }

    @Transactional
    public long createWithTx(String title, String joinCode, long hostVisitorId, boolean failBetween) {
        long pollId = repo.insertPoll(title, joinCode);
        if (failBetween) {
            throw new IllegalStateException("2本のINSERTの間で失敗した");
        }
        repo.insertParticipant(pollId, hostVisitorId, "HOST");
        return pollId;
    }
}
