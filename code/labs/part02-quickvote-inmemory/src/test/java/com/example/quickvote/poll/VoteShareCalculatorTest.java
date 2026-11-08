package com.example.quickvote.poll;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 得票率（最大剰余法）の単体テスト。Spring は使わない。 */
class VoteShareCalculatorTest {

    static List<Arguments> cases() {
        return List.of(
                // 3等分は単純な四捨五入だと 99.9 になる。残りの 0.1 を先頭に配る。
                Arguments.of(List.of(1, 1, 1), List.of("33.4", "33.3", "33.3")),
                Arguments.of(List.of(2, 1), List.of("66.7", "33.3")),
                Arguments.of(List.of(1, 2, 4), List.of("14.3", "28.6", "57.1")),
                Arguments.of(List.of(5), List.of("100.0")),
                Arguments.of(List.of(0, 5), List.of("0.0", "100.0")),
                // 票が0件なら全部 0.0（0除算にしない）
                Arguments.of(List.of(0, 0, 0), List.of("0.0", "0.0", "0.0")),
                Arguments.of(List.of(), List.of()));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void 得票率は小数1桁で合計が100になる(List<Integer> counts, List<String> expected) {
        List<BigDecimal> shares = VoteShareCalculator.calculate(counts);

        assertThat(shares).extracting(BigDecimal::toPlainString).containsExactlyElementsOf(expected);
    }

    @Test
    void ランダムな票数でも合計はちょうど100_0で各値は理論値との差が0_1未満() {
        Random random = new Random(42); // 再現できるよう seed を固定
        for (int round = 0; round < 2000; round++) {
            int options = 1 + random.nextInt(10);
            List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < options; i++) {
                counts.add(random.nextInt(60));
            }
            long total = counts.stream().mapToLong(Integer::longValue).sum();

            List<BigDecimal> shares = VoteShareCalculator.calculate(counts);

            BigDecimal sum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(total == 0 ? "0.0" : "100.0");
            for (int i = 0; i < options && total > 0; i++) {
                BigDecimal exact = BigDecimal.valueOf(counts.get(i) * 100.0 / total);
                assertThat(shares.get(i).subtract(exact).abs()).isLessThan(new BigDecimal("0.1"));
                assertThat(shares.get(i).scale()).isEqualTo(1);
            }
        }
    }

    @Test
    void 端数の0_1は剰余が大きい択に配る() {
        // 1/3 → 333.33…、2/3 → 666.66…。切り捨て合計は 999 で、足りない1単位は剰余の大きい 2票側へ
        List<BigDecimal> shares = VoteShareCalculator.calculate(List.of(1, 2));

        assertThat(shares).extracting(BigDecimal::toPlainString).containsExactly("33.3", "66.7");
    }
}
