package com.example.quickvote.security;

import java.time.Duration;
import java.time.Instant;

/**
 * トークンバケット1個。「バケツに最大 capacity 個のトークンが入る。1リクエストで1個使う。
 * トークンは一定の速さで補充される。空なら拒否」という仕組み。
 * <ul>
 * <li>capacity: 一度にまとめて許す回数（バースト）</li>
 * <li>refillPerSecond: 長い目で見た許容レート</li>
 * </ul>
 * 補充は「時間を見て計算し直す」方式で、補充用のスレッドやタイマーは要らない。
 * 時刻は引数で受け取る（Clock から取った値を呼び出し側が渡す）ので、テストで時間を進められる。
 * スレッドセーフではない。状態を持つ1個のバケットを同時に触らせないのは、TokenBucketLimiter の役目
 * （ConcurrentHashMap#compute の中でしか触らないので、同じキーの操作は直列になる）。
 */
public final class TokenBucket {

    /** 判定結果。拒否のとき retryAfter は「次の1トークンが貯まるまでの時間」。 */
    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    private final int capacity;
    private final double refillPerSecond;
    private double tokens;
    private Instant lastRefill;

    public TokenBucket(int capacity, double refillPerSecond, Instant now) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity; // 最初は満タン
        this.lastRefill = now;
    }

    public Decision tryConsume(Instant now) {
        refill(now);
        if (tokens >= 1) {
            tokens -= 1;
            return Decision.ALLOWED;
        }
        double secondsUntilNextToken = (1 - tokens) / refillPerSecond;
        return new Decision(false, Duration.ofMillis((long) Math.ceil(secondsUntilNextToken * 1000)));
    }

    /** 満タン＝しばらく使われていない。掃除の対象にしてよい。 */
    public boolean isIdle(Instant now) {
        refill(now);
        return tokens >= capacity;
    }

    private void refill(Instant now) {
        double elapsedSeconds = Math.max(0, Duration.between(lastRefill, now).toNanos() / 1_000_000_000.0);
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefill = now;
    }
}
