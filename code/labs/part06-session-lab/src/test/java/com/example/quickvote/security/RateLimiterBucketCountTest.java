package com.example.quickvote.security;

import java.time.Instant;

import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.common.Tokens;
import com.example.quickvote.support.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第35章：IP単位で拒否した後も、偽の Cookie ごとに訪問者単位のバケットを作ってしまうと、メモリが増え続ける。
 */
class RateLimiterBucketCountTest {

    private static RateLimiter limiter(boolean bucketOnIpReject) {
        QuickVoteProperties props = new QuickVoteProperties(new QuickVoteProperties.VisitorCookie(false),
                new QuickVoteProperties.RateLimit(5, 0.0001, 10, 10, 10, 10)); // IP は容量5で、ほぼ補充されない
        LabProperties lab = new LabProperties(true, "path-pattern", true, "", 8, true, bucketOnIpReject);
        return new RateLimiter(props, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")), lab);
    }

    private static void attackWithFakeCookies(RateLimiter limiter, int n) {
        for (int i = 0; i < n; i++) {
            String fakeKey = Tokens.sha256Hex(Tokens.newToken()); // 毎回違う（出まかせの）Cookie
            limiter.checkAttempt("203.0.113.9", fakeKey);
        }
    }

    @Test
    void 元のバグ_拒否されていても偽Cookieの数だけバケットが増える() {
        RateLimiter limiter = limiter(true);

        attackWithFakeCookies(limiter, 10_000);

        assertThat(limiter.ipBucketCount()).isEqualTo(1);
        assertThat(limiter.visitorBucketCount()).isEqualTo(10_000);
    }

    @Test
    void 修正後_IPで拒否したらそこで終わり_バケットは容量の分しか増えない() {
        RateLimiter limiter = limiter(false);

        attackWithFakeCookies(limiter, 10_000);

        assertThat(limiter.ipBucketCount()).isEqualTo(1);
        assertThat(limiter.visitorBucketCount()).isEqualTo(5); // IP が通した5回分だけ
    }
}
