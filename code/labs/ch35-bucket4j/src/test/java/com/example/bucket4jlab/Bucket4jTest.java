package com.example.bucket4jlab;

import java.time.Duration;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自作の TokenBucket（容量10・毎分10個補充）と同じものを、Bucket4j で書く。
 * 時刻は TimeMeter で差し替えられる（自作版が Clock を引数に取るのと同じ考え方）。
 */
class Bucket4jTest {

    /** テスト用の時計。ナノ秒を自分で進める。 */
    static class ManualTime implements TimeMeter {
        long nanos;

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    @Test
    void 容量10_毎分10個補充_11回目は拒否され_待ち時間が分かる() {
        ManualTime time = new ManualTime();
        Bucket bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillGreedy(10, Duration.ofMinutes(1)))
                .withCustomTimePrecision(time)
                .build();

        for (int i = 0; i < 10; i++) {
            assertThat(bucket.tryConsume(1)).isTrue();
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        assertThat(probe.isConsumed()).isFalse();
        // 次の1個が貯まるまで6秒（自作版の Retry-After と同じ計算）
        assertThat(Duration.ofNanos(probe.getNanosToWaitForRefill())).isEqualTo(Duration.ofSeconds(6));

        time.nanos += Duration.ofSeconds(6).toNanos();
        assertThat(bucket.tryConsume(1)).isTrue();
    }
}
