package com.example.quickvote.vote;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/** 得票率の計算。合計がちょうど target（100 や 1000）になるように最大剰余法で配分する。 */
public final class Percentages {

    private Percentages() {}

    /**
     * 最大剰余法で整数を配分する。
     * 剰余が同じ場合は票数が多い方、それも同じなら先に並んだ選択肢を優先する。
     *
     * @param counts 選択肢ごとの票数（負の値は不可）
     * @param target 配分する合計（百分率なら 100、0.1% 単位なら 1000）
     * @return counts と同じ長さの配列。総票数が 0 のときは全て 0
     */
    public static int[] allocate(int[] counts, int target) {
        long total = 0;
        for (int c : counts) {
            if (c < 0) {
                throw new IllegalArgumentException("票数が負です: " + c);
            }
            total += c;
        }
        int[] result = new int[counts.length];
        if (total == 0) {
            return result;
        }

        long[] remainder = new long[counts.length];
        int assigned = 0;
        for (int i = 0; i < counts.length; i++) {
            long scaled = (long) counts[i] * target;
            result[i] = (int) (scaled / total);
            remainder[i] = scaled % total;
            assigned += result[i];
        }

        int leftover = target - assigned;
        Integer[] order = IntStream.range(0, counts.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator
                .comparingLong((Integer i) -> remainder[i]).reversed()
                .thenComparing(Comparator.comparingInt((Integer i) -> counts[i]).reversed()));
        for (int k = 0; k < leftover; k++) {
            result[order[k]]++;
        }
        return result;
    }
}
