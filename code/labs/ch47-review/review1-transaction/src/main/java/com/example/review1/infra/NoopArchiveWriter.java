package com.example.review1.infra;

import org.springframework.stereotype.Component;

@Component
public class NoopArchiveWriter implements ArchiveWriter {

    @Override
    public void writeSnapshot(long pollId) {
        // 演習用: 何もしない。テストが例外を投げる版に差し替える。
    }
}
