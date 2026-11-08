package com.example.quickvote.security;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 第33章：URL の判定。生の URI 文字列の比較は、パーセントエンコードでかわされる。 */
class EntryMatchersTest {

    @Test
    void 文字列の比較はエンコードされたURLを見逃し_PathPatternは見逃さない() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/%6aoin"); // %6a = j

        // 元の実装：getRequestURI() は生の文字列を返す
        assertThat(request.getRequestURI()).isEqualTo("/api/%6aoin");
        assertThat("/api/join".equals(request.getRequestURI())).isFalse();

        // 直した実装：MVC と同じ、デコード後のパスで判定する
        assertThat(EntryMatchers.JOIN.matches(request)).isTrue();
        assertThat(EntryMatchers.isAttempt(request)).isTrue();
    }
}
