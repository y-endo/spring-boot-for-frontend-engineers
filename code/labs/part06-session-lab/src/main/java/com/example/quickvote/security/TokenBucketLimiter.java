package com.example.quickvote.security;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * キー（IPアドレスや訪問者）ごとに TokenBucket を持つ表。
 * <p>
 * 「消費」も「掃除（削除）」も、ConcurrentHashMap の compute 系メソッドの中で行う。compute は同じキーに対する操作を
 * 直列化するので、次のような競合が起きない:
 * <ol>
 * <li>掃除が「満タン（＝使われていない）」と判定する</li>
 * <li>その間に別のリクエストが同じバケットからトークンを消費する</li>
 * <li>掃除が（消費済みの）そのバケットを捨てる → 次のリクエストは満タンの新しいバケットを得て、制限がリセットされる</li>
 * </ol>
 * 「map.get して tryConsume」「isIdle して remove」のように、取得と更新を別々の操作に分けると、その隙間に割り込まれる。
 * バケット内部の synchronized だけでは、この隙間は塞げない（バケットを map から取り出した後・削除する前の話なので）。
 * <p>
 * 限界（複数台構成）: バケットはこのJVMのメモリにある。アプリを2台並べると、それぞれが別々に数えるので、
 * 実質の許容回数は台数倍になる。台数に関係なく正確に制限したいなら、Redis や DB など
 * 全台から見える場所にカウンターを置く必要がある（第35章でライブラリ Bucket4j と併せて触れる）。
 * ここでは仕組みを読みやすくするため、あえて自作のメモリ実装にしている。
 */
public class TokenBucketLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, double refillPerMinute, Clock clock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerMinute / 60.0;
        this.clock = clock;
    }

    public TokenBucket.Decision tryAcquire(String key) {
        TokenBucket.Decision[] result = new TokenBucket.Decision[1]; // ラムダの外へ結果を取り出すための入れ物
        buckets.compute(key, (k, bucket) -> {
            // 時刻の取得もこの中で行う（キーごとの直列化の内側。テストで「消費の最中」を止められる）。
            Instant now = clock.instant();
            TokenBucket current = bucket != null ? bucket : new TokenBucket(capacity, refillPerSecond, now);
            result[0] = current.tryConsume(now);
            return current;
        });
        return result[0];
    }

    /**
     * 満タンに戻ったバケットを捨てる。やらないと、アクセスしてきたキーの数だけメモリが増え続ける。
     * 捨てても次のアクセスで満タンから作り直されるだけなので、制限の意味は変わらない
     * （ただし「消費の途中のバケット」を捨てると意味が変わるので、判定と削除を compute の中で一体にしている）。
     */
    public void evictIdle() {
        for (String key : buckets.keySet()) {
            buckets.computeIfPresent(key, (k, bucket) -> bucket.isIdle(clock.instant()) ? null : bucket);
        }
    }

    public int size() {
        return buckets.size();
    }
}
