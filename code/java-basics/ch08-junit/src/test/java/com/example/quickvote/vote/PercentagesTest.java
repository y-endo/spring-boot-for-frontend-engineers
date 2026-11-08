package com.example.quickvote.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PercentagesTest {

    @Test
    @DisplayName("3人が1票ずつなら 34/33/33（余りは先の選択肢へ）")
    void oneVoteEach() {
        assertThat(Percentages.allocate(new int[] {1, 1, 1}, 100)).containsExactly(34, 33, 33);
    }

    @Test
    @DisplayName("0.1%単位でも合計がちょうど1000になる")
    void permille() {
        assertThat(Percentages.allocate(new int[] {1, 1, 1}, 1000)).containsExactly(334, 333, 333);
    }

    @Test
    void zeroVotesGivesAllZero() {
        assertThat(Percentages.allocate(new int[] {0, 0, 0}, 100)).containsExactly(0, 0, 0);
    }

    @Test
    void emptyArrayGivesEmptyArray() {
        assertThat(Percentages.allocate(new int[] {}, 100)).isEmpty();
    }

    @Test
    void negativeCountIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Percentages.allocate(new int[] {3, -1}, 100))
                .withMessageContaining("-1");
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(new int[] {5, 3, 2, 0}, new int[] {50, 30, 20, 0}),
                Arguments.of(new int[] {2, 3, 3}, new int[] {25, 38, 37}), // 剰余が同じなら先の選択肢
                Arguments.of(new int[] {1}, new int[] {100}),
                Arguments.of(new int[] {0, 7}, new int[] {0, 100}));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cases")
    void expectedAllocations(int[] counts, int[] expected) {
        assertThat(Percentages.allocate(counts, 100)).containsExactly(expected);
    }

    @Test
    @DisplayName("どんな票数でも、合計は必ず100になり、票の多い方が少ない方を下回らない")
    void invariantsHoldForManyInputs() {
        Random random = new Random(42); // シードを固定＝再現できる
        for (int round = 0; round < 10_000; round++) {
            int[] counts = random.ints(1 + random.nextInt(10), 0, 50).toArray();
            int[] result = Percentages.allocate(counts, 100);

            int total = Arrays.stream(counts).sum();
            if (total > 0) {
                assertThat(Arrays.stream(result).sum()).as("counts=%s", Arrays.toString(counts)).isEqualTo(100);
            }
            for (int i = 0; i < counts.length; i++) {
                for (int j = 0; j < counts.length; j++) {
                    if (counts[i] > counts[j]) {
                        assertThat(result[i])
                                .as("counts=%s", Arrays.toString(counts))
                                .isGreaterThanOrEqualTo(result[j]);
                    }
                }
            }
        }
    }
}
