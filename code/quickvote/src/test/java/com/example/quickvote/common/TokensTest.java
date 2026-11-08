package com.example.quickvote.common;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokensTest {

    @Test
    void トークンは32バイトの乱数をBase64URLにした43文字() {
        String token = Tokens.newToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void トークンは毎回異なる() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(Tokens.newToken());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    void SHA256は既知の値と一致する() {
        // SHA-256("abc") の公式テストベクタ
        assertThat(Tokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
