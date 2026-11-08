package com.example.quickvote.common;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 自作の Bean Validation 制約。@Size は UTF-16 の char 数を数えるが、これはコードポイント数とバイト数で数える。 */
class BoundedTextValidatorTest {

    record Sample(@BoundedText(maxChars = 100, maxBytes = 400) String value) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private boolean valid(String value) {
        return validator.validate(new Sample(value)).isEmpty();
    }

    @Test
    void 英数字は100文字まで() {
        assertThat(valid("a".repeat(100))).isTrue();
        assertThat(valid("a".repeat(101))).isFalse();
    }

    @Test
    void 日本語は100文字まで_300バイト() {
        assertThat(valid("あ".repeat(100))).isTrue();
        assertThat(valid("あ".repeat(101))).isFalse();
    }

    @Test
    void 絵文字は1文字として数える_charでは2個だが100文字まで通る() {
        String hundredEmoji = "😀".repeat(100); // UTF-16 では 200 char、UTF-8 では 400 バイト
        assertThat(hundredEmoji.length()).isEqualTo(200);
        assertThat(valid(hundredEmoji)).isTrue();
        assertThat(valid("😀".repeat(101))).isFalse();
    }

    @Test
    void nullは検証しない_必須かどうかはNotBlankの役目() {
        assertThat(valid(null)).isTrue();
    }

    @Test
    void バイト数の上限も効く() {
        record Tight(@BoundedText(maxChars = 100, maxBytes = 10) String value) {
        }

        assertThat(validator.validate(new Tight("あいう")).isEmpty()).isTrue(); // 9バイト
        assertThat(validator.validate(new Tight("あいうえ")).isEmpty()).isFalse(); // 12バイト
    }
}
