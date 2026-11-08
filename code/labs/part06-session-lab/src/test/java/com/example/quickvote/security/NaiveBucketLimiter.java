package com.example.quickvote.security;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第35章：「取得」と「更新」を別々の操作に分けた、元の（競合のある）トークンバケット表。
 * バケット1個の中は synchronized で守ってあるつもりでも、Map から取り出した後・削除する前の隙間が空いている。
 * テストだけで使う。本線には置かない。
 */
class NaiveBucketLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    NaiveBucketLimiter(int capacity, double refillPerMinute, Clock clock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerMinute / 60.0;
        this.clock = clock;
    }

    TokenBucket.Decision tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillPerSecond, clock.instant()));
        // ↑ここでバケットを手にした。この後に掃除が Map から捨てても、手元のバケットはそのまま使える
        Instant now = clock.instant(); // 時刻を取る。ロックの外なので、ここで止まっても掃除は進める
        synchronized (bucket) {
            return bucket.tryConsume(now);
        }
    }

    void evictIdle() {
        for (String key : buckets.keySet()) {
            TokenBucket bucket = buckets.get(key);
            if (bucket != null) {
                boolean idle;
                synchronized (bucket) {
                    idle = bucket.isIdle(clock.instant());
                }
                if (idle) {
                    buckets.remove(key); // 判定と削除の間に、別のリクエストが消費しているかもしれない
                }
            }
        }
    }
}
