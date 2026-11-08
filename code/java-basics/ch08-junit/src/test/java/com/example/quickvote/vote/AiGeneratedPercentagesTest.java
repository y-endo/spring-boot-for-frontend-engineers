package com.example.quickvote.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class AiGeneratedPercentagesTest {

    /** 失敗する様子を観察するためのテスト。普段は pom.xml の設定で除外している。 */
    @Test
    @Tag("demo-fail")
    void naiveImplementationSumsTo100() {
        int[] result = NaivePercentages.calc(new int[] {2, 3, 3});
        assertThat(Arrays.stream(result).sum()).isEqualTo(100);
    }

    /** バグを「事実」として固定するテスト（特性テスト）。直したら消す。 */
    @Test
    void naiveImplementationIsKnownToBeBroken() {
        int[] result = NaivePercentages.calc(new int[] {2, 3, 3});
        assertThat(result).containsExactly(25, 38, 38);
        assertThat(Arrays.stream(result).sum()).isEqualTo(101);
    }
}
