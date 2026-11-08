package com.example.quickvote.poll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 得票率（%）を小数1桁で計算し、合計をちょうど100.0にする。最大剰余法（ヘア・ニーマイヤー法）。
 * <p>
 * 単純に四捨五入すると 1票ずつ3択で 33.3 + 33.3 + 33.3 = 99.9 になる。
 * 手順: ① 0.1%を1単位として、各択の取り分(count/total×1000)を切り捨てる。
 * ② 足りない単位数(1000 - 切り捨て合計)を、剰余が大きい順に1単位ずつ配る。
 * 計算はすべて BigDecimal で行い、double の誤差を持ち込まない。
 */
public final class VoteShareCalculator {

    private static final BigDecimal UNITS = BigDecimal.valueOf(1000);

    private VoteShareCalculator() {
    }

    /** counts と同じ順序で、各択の得票率（scale=1）を返す。総票数0なら全部 0.0。 */
    public static List<BigDecimal> calculate(List<Integer> counts) {
        long total = counts.stream().mapToLong(Integer::longValue).sum();
        int size = counts.size();
        if (total == 0) {
            return counts.stream().map(c -> BigDecimal.valueOf(0, 1)).toList();
        }

        BigDecimal totalDecimal = BigDecimal.valueOf(total);
        long[] units = new long[size];
        BigDecimal[] remainders = new BigDecimal[size];
        long assigned = 0;
        for (int i = 0; i < size; i++) {
            // 整数部（切り捨てた取り分）と剰余に分ける。
            BigDecimal[] quotientAndRemainder =
                    BigDecimal.valueOf(counts.get(i)).multiply(UNITS).divideAndRemainder(totalDecimal);
            units[i] = quotientAndRemainder[0].setScale(0, RoundingMode.DOWN).longValueExact();
            remainders[i] = quotientAndRemainder[1];
            assigned += units[i];
        }

        // 剰余が大きい順。同じなら票が多い方、それも同じなら先頭側を優先して、結果を一意に決める。
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparing((Integer i) -> remainders[i]).reversed()
                .thenComparing(i -> counts.get(i), Comparator.reverseOrder())
                .thenComparing(i -> i));

        long leftover = UNITS.longValue() - assigned;
        for (int k = 0; k < leftover; k++) {
            units[order.get(k)]++;
        }

        List<BigDecimal> shares = new ArrayList<>();
        for (long u : units) {
            shares.add(BigDecimal.valueOf(u, 1)); // 334 → 33.4
        }
        return shares;
    }
}
