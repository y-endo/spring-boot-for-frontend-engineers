package com.example.quickvote.support;

import java.time.Duration;

import com.example.quickvote.common.QuickVoteProperties;

/** Spring を起動しない単体テストで使う設定値（既定値と同じ）。 */
public final class TestProperties {

    private TestProperties() {
    }

    public static QuickVoteProperties defaults() {
        return withSse(new QuickVoteProperties.Sse(Duration.ofSeconds(15), Duration.ofMinutes(30),
                Duration.ofSeconds(10), 4));
    }

    public static QuickVoteProperties withSse(QuickVoteProperties.Sse sse) {
        return new QuickVoteProperties(
                new QuickVoteProperties.VisitorCookie(false),
                new QuickVoteProperties.RateLimit(100, 60, 10, 10, 10, 10),
                sse,
                new QuickVoteProperties.Cleanup(Duration.ofMinutes(5), 100, 50),
                new QuickVoteProperties.Idempotency(Duration.ofHours(24)));
    }
}
