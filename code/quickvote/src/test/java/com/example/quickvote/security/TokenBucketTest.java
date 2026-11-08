package com.example.quickvote.security;

import java.time.Duration;
import java.time.Instant;

import com.example.quickvote.support.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** トークンバケットの単体テスト。時刻は自分で進めるので、待たずに「時間経過」を再現できる。 */
class TokenBucketTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void 容量の回数までは連続で許可し_超えると拒否する() {
        TokenBucket bucket = new TokenBucket(3, 1.0, T0);

        assertThat(bucket.tryConsume(T0).allowed()).isTrue();
        assertThat(bucket.tryConsume(T0).allowed()).isTrue();
        assertThat(bucket.tryConsume(T0).allowed()).isTrue();
        assertThat(bucket.tryConsume(T0).allowed()).isFalse();
    }

    @Test
    void 拒否したときは次のトークンが貯まるまでの時間を返す() {
        TokenBucket bucket = new TokenBucket(1, 0.5, T0); // 2秒で1トークン
        bucket.tryConsume(T0);

        TokenBucket.Decision decision = bucket.tryConsume(T0);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void 時間が経つとトークンが補充される() {
        TokenBucket bucket = new TokenBucket(1, 1.0, T0); // 1秒で1トークン
        bucket.tryConsume(T0);
        assertThat(bucket.tryConsume(T0.plusMillis(500)).allowed()).isFalse();

        assertThat(bucket.tryConsume(T0.plusMillis(1000)).allowed()).isTrue();
    }

    @Test
    void 補充は容量を超えて貯まらない() {
        TokenBucket bucket = new TokenBucket(2, 1.0, T0);

        // 1時間放置しても貯まるのは容量の2個まで
        Instant later = T0.plus(Duration.ofHours(1));
        assertThat(bucket.tryConsume(later).allowed()).isTrue();
        assertThat(bucket.tryConsume(later).allowed()).isTrue();
        assertThat(bucket.tryConsume(later).allowed()).isFalse();
    }

    @Test
    void キーごとに独立したバケットを持ち_満タンに戻ったものは掃除される() {
        MutableClock clock = new MutableClock(T0);
        TokenBucketLimiter limiter = new TokenBucketLimiter(1, 60.0, clock); // 1秒で1トークン

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue(); // a の消費は b に影響しない
        assertThat(limiter.size()).isEqualTo(2);

        limiter.evictIdle(); // どちらもまだ満タンではない
        assertThat(limiter.size()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(2));
        limiter.evictIdle();
        assertThat(limiter.size()).isZero();
    }
}
