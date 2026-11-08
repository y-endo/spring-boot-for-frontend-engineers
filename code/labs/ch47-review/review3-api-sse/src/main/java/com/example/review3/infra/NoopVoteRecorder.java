package com.example.review3.infra;

import org.springframework.stereotype.Component;

@Component
public class NoopVoteRecorder implements VoteRecorder {

    @Override
    public void record(long optionId) {
        // 演習用: 何もしない。
    }
}
