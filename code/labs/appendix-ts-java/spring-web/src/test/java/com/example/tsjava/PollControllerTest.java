package com.example.tsjava;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Vitest + supertest:  const res = await request(app).post("/api/polls").send({ title: "" }); expect(res.status).toBe(400)
@WebMvcTest(PollController.class)
class PollControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 作成すると201とLocationが返る() {
        var result = mvc.post().uri("/api/polls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"ランチ\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().containsHeader("Location");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("ランチ");
    }

    @Test
    void 空のタイトルは400() {
        var result = mvc.post().uri("/api/polls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 存在しない投票は404のProblemDetail() {
        var result = mvc.get().uri("/api/polls/999").exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void フィルターが応答ヘッダーを足す() {
        var result = mvc.get().uri("/api/polls/999").exchange();
        assertThat(result).headers().containsHeader("X-Filter");
    }

    @Test
    void Jacksonで往復できる() {
        assertThat(JacksonDemo.run()).contains("\"title\":\"ランチ\"");
    }
}
