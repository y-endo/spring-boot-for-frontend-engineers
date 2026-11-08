package com.example.lab.ch26;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * 有限回の再試行。
 * <ul>
 * <li>再試行するのは「やり直せば成功しうる」競合の失敗だけ（デッドロック、直列化失敗、楽観ロックの競合）。
 * 入力エラーや制約違反は何度やっても同じなので再試行しない。</li>
 * <li>回数に上限がある（無限ループにしない）。待ち時間は少しずつ伸ばし、ばらつきも足す（全員が同時にやり直さない）。</li>
 * <li>呼ぶ側は、<b>トランザクションの外</b>で呼ぶこと。失敗したトランザクションの中でやり直しても、もう使えない。</li>
 * </ul>
 */
public final class Retries {

    private Retries() {
    }

    public static boolean isRetryable(Throwable e) {
        return e instanceof PessimisticLockingFailureException || e instanceof OptimisticLockingFailureException;
    }

    public static <T> T onConflict(int maxAttempts, Supplier<T> action) {
        for (int attempt = 1;; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    throw e;
                }
                sleep(10L * (1L << attempt) + ThreadLocalRandom.current().nextLong(10));
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }
}
