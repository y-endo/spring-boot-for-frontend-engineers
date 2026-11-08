package com.example.quickvote.poll;

import jakarta.validation.Valid;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第14章: @Valid を付け忘れると、DTO に書いた検証の注釈は「何もしない飾り」になる。
 * Spring の起動なしに、Controller だけを MockMvc に載せて確かめる（スタンドアロン構成）。
 */
class ValidAnnotationTest {

    @RestController
    static class Controllers {

        @PostMapping("/with-valid")
        String withValid(@Valid @RequestBody CreatePollRequest request) {
            return "reached: " + request.title();
        }

        @PostMapping("/without-valid")
        String withoutValid(@RequestBody CreatePollRequest request) { // @Valid が無い
            return "reached: " + request.title();
        }
    }

    private final MockMvcTester mvc = MockMvcTester.of(new Controllers());

    private static final String BLANK_TITLE = "{\"title\":\"   \",\"closesInMinutes\":30}";

    @Test
    void Validがあれば_空のタイトルはControllerの前で止まる() {
        var result = mvc.post().uri("/with-valid").contentType(MediaType.APPLICATION_JSON).content(BLANK_TITLE)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void Validを付け忘れると_検証されずControllerまで届く() {
        var result = mvc.post().uri("/without-valid").contentType(MediaType.APPLICATION_JSON).content(BLANK_TITLE)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().isEqualTo("reached: "); // 空のタイトルのまま、業務の処理に進んでしまう
    }
}
