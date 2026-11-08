package com.example.quickvote.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.example.quickvote.support.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第35章：掃除（evictIdle）と消費（tryAcquire）の競合。
 * 起こしたい順序: ①消費がバケットを手にする → ②その最中に掃除が「満タン＝不要」と判断して捨てる
 * → ③消費が終わる（捨てられたバケットから取る）→ ④次のリクエストは満タンの新しいバケットを得る＝制限のリセット。
 * 「消費の最中」を作るため、指定したスレッドの最初の instant() を、テストが許可するまで止める時計を使う。
 */
class BucketRaceTest {

    private static final class GateClock extends Clock {
        private final MutableClock delegate;
        private final AtomicBoolean armed = new AtomicBoolean();
        private volatile Thread gatedThread;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        GateClock(MutableClock delegate) {
            this.delegate = delegate;
        }

        void gate(Thread thread) {
            this.gatedThread = thread;
            this.armed.set(true);
        }

        @Override
        public Instant instant() {
            if (Thread.currentThread() == gatedThread && armed.compareAndSet(true, false)) {
                reached.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.instant();
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private interface Limiter {
        boolean tryAcquire(String key);

        void evictIdle();
    }

    /**
     * 容量2・補充は毎分1個。1回消費して10分進める（次に触れば満タン＝掃除の対象になりうる状態）。
     * 別スレッドの消費を途中で止め、その最中に掃除を走らせる。再開したあと、さらに2回試して、2回目が許可されるかを返す。
     * 消費は済んでいるので、本来は「あと1回」しか許可されない。
     */
    private boolean secondAfterInterleavingIsAllowed(Function<Clock, Limiter> factory) throws Exception {
        MutableClock base = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GateClock clock = new GateClock(base);
        Limiter limiter = factory.apply(clock);

        limiter.tryAcquire("k");
        base.advance(Duration.ofMinutes(10));

        Thread consumer = new Thread(() -> limiter.tryAcquire("k"));
        clock.gate(consumer);
        consumer.start();
        assertThat(clock.reached.await(10, TimeUnit.SECONDS)).as("消費が途中まで進んだ").isTrue();

        Thread evictor = new Thread(limiter::evictIdle);
        evictor.start();
        evictor.join(300); // 競合のない実装では、掃除は消費の終わりを待たされる（ここでは終わらない）
        clock.release.countDown();
        consumer.join(10_000);
        evictor.join(10_000);

        limiter.tryAcquire("k");
        return limiter.tryAcquire("k");
    }

    @Test
    void 取得と更新を分けた元の実装では_消費済みのバケットが捨てられて制限がリセットされる() throws Exception {
        boolean allowed = secondAfterInterleavingIsAllowed(clock -> {
            NaiveBucketLimiter naive = new NaiveBucketLimiter(2, 1.0, clock);
            return new Limiter() {
                public boolean tryAcquire(String key) {
                    return naive.tryAcquire(key).allowed();
                }

                public void evictIdle() {
                    naive.evictIdle();
                }
            };
        });

        assertThat(allowed).as("制限がリセットされた（バグの再現）").isTrue();
    }

    @Test
    void computeの中で消費も掃除も行う実装では_リセットされない() throws Exception {
        boolean allowed = secondAfterInterleavingIsAllowed(clock -> {
            TokenBucketLimiter fixed = new TokenBucketLimiter(2, 1.0, clock);
            return new Limiter() {
                public boolean tryAcquire(String key) {
                    return fixed.tryAcquire(key).allowed();
                }

                public void evictIdle() {
                    fixed.evictIdle();
                }
            };
        });

        assertThat(allowed).as("消費済みのバケットが捨てられていない").isFalse();
    }
}
