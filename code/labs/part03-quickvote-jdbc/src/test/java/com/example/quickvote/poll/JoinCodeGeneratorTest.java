package com.example.quickvote.poll;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeGeneratorTest {

    private final JoinCodeGenerator generator = new JoinCodeGenerator();

    @Test
    void 文字集合は紛らわしい文字を除いた31種() {
        assertThat(JoinCodeGenerator.ALPHABET).hasSize(31);
        assertThat(JoinCodeGenerator.ALPHABET).doesNotContain("0", "O", "1", "I", "L");
        assertThat(JoinCodeGenerator.ALPHABET.chars().distinct().count()).isEqualTo(31);
    }

    @Test
    void 生成されるコードは8文字で許可された文字だけ() {
        for (int i = 0; i < 5000; i++) {
            String code = generator.generate();

            assertThat(code).hasSize(8);
            assertThat(JoinCodeGenerator.isWellFormed(code)).isTrue();
        }
    }

    @Test
    void 大量に生成してもほぼ重複しない() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            codes.add(generator.generate());
        }

        // 31^8 ≒ 8.5×10^11 通り。2万個で重複が出る確率は無視できるほど小さい。
        assertThat(codes).hasSize(20000);
    }

    @Test
    void 利用者の入力ゆれ_空白と小文字_を吸収する() {
        assertThat(JoinCodeGenerator.normalize("  abcd2345 ")).isEqualTo("ABCD2345");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "ABCD234", "ABCD23456", "ABCD234O", "ABCD2341", "abcd2345", "ABCD 345" })
    void 形式が正しくないコードは不正と判定する(String code) {
        assertThat(JoinCodeGenerator.isWellFormed(code)).isFalse();
    }
}
