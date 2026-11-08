package com.example.quickvote.journey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第12章の実験用。リクエストの通り道の各地点で、同じ書式で1行ずつログを出す。
 * 書式: [journey] 地点 | 内容 | thread=処理しているスレッドの名前
 * journey プロファイルのときだけ使う（本番のコードには含まれない）。
 */
final class Journey {

    private static final Logger log = LoggerFactory.getLogger("journey");

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private Journey() {
    }

    static void step(String stage, String detail) {
        log.info("[journey] {} | {} | thread={}", stage, detail, Thread.currentThread().getName());
    }

    /** メソッド呼び出しの入れ子の深さ（インデント用）を返し、1段深くする。 */
    static int enter() {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        return depth;
    }

    static void exit() {
        DEPTH.set(Math.max(0, DEPTH.get() - 1));
    }
}
