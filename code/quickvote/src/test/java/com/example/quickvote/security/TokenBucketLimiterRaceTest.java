package com.example.quickvote.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.quickvote.support.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回数制限の掃除（evictIdle）と消費（tryAcquire）の競合。
 * <p>
 * 起こしたい順序: ①消費が始まる（バケットを手にした）→ ②その最中に掃除が「満タン＝不要」と判断して捨てる
 * → ③消費が終わる（捨てられたバケットから取る）→ ④次のリクエストは満タンの新しいバケットを得る＝制限のリセット。
 * 「消費の最中」を止めるため、Clock を細工している。消費は時刻の取得を、バケットを手にした後に行う。
 * そこで最初の1回の instant() 呼び出しだけを、テストが許可するまで止める。
 */
class TokenBucketLimiterRaceTest {

    /** 指定したスレッドの最初の instant() だけを、release されるまで止める時計。 */
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

    @Test
    void 消費の最中に掃除が走っても_消費済みのバケットは捨てられず_制限がリセットされない() throws Exception {
        MutableClock base = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GateClock clock = new GateClock(base);
        TokenBucketLimiter limiter = new TokenBucketLimiter(2, 1.0, clock); // 容量2、補充は毎分1個

        limiter.tryAcquire("k"); // バケットができる（残り1個）
        base.advance(Duration.ofMinutes(10)); // 十分に時間が経ち、次に触れば満タンに戻っている＝掃除の対象になりうる状態

        // ① 別スレッドで消費を始め、バケットを手にした直後（時刻の取得）で止める
        Thread consumer = new Thread(() -> limiter.tryAcquire("k"));
        clock.gate(consumer);
        consumer.start();
        assertThat(clock.reached.await(10, TimeUnit.SECONDS)).as("消費が途中まで進んだ").isTrue();

        // ② その最中に掃除を走らせる。バケットを捨てられてはいけない（消費が終わるまで、掃除は待たされる）
        Thread evictor = new Thread(limiter::evictIdle);
        evictor.start();
        Thread.sleep(200); // 掃除スレッドが、消費の終わりを待つ状態に入る時間
        // ③ 消費を再開する
        clock.release.countDown();
        consumer.join(10_000);
        evictor.join(10_000);

        // ④ 消費は1回済んでいる（容量2のうち残り1個）。次の1回は許可され、その次は拒否される。
        // バケットが捨てられていたら、満タンの新しいバケットになり、2回とも許可されてしまう。
        assertThat(limiter.tryAcquire("k").allowed()).isTrue();
        assertThat(limiter.tryAcquire("k").allowed()).as("消費済みのバケットが捨てられ、制限がリセットされた").isFalse();
    }

    @Test
    void 同じキーへの並列の消費でも_容量を超えて許可されない() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketLimiter limiter = new TokenBucketLimiter(50, 0.001, clock);
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] threads = new Thread[8];
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int n = 0; n < 100; n++) {
                    if (limiter.tryAcquire("same").allowed()) {
                        allowed.incrementAndGet();
                    }
                    if (n % 10 == 0) {
                        limiter.evictIdle(); // 消費と掃除が交互に走っても、数えがずれない
                    }
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(30_000);
        }

        assertThat(allowed.get()).isEqualTo(50);
    }
}
