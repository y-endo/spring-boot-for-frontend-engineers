package com.example.quickvote.vote;

import java.util.Arrays;

/** 「AIが書いた」という想定の実装。各選択肢を四捨五入しているだけ。 */
final class NaivePercentages {

    private NaivePercentages() {}

    static int[] calc(int[] counts) {
        long total = Arrays.stream(counts).asLongStream().sum();
        int[] result = new int[counts.length];
        if (total == 0) {
            return result;
        }
        for (int i = 0; i < counts.length; i++) {
            result[i] = (int) Math.round(counts[i] * 100.0 / total);
        }
        return result;
    }
}
