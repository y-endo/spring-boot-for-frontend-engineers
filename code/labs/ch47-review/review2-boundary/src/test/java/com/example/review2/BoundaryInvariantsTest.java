package com.example.review2;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * 「守る約束」をそのままテストにしたもの。bad にも fixed にも同じテストを流す。
 * bad では赤（失敗）になるのが正しい。fixed では緑になる。
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class BoundaryInvariantsTest {

    static final String COOKIE = "QV_VISITOR";

    @Autowired
    MockMvcTester mvc;

    /** 訪問者と、その人が主催する投票。 */
    record Host(Cookie cookie, long pollId) {
    }

    static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 新しい訪問者として投票を作る（CSRF トークンつきの正規のリクエスト）。 */
    Host newHostWithPoll() {
        MvcTestResult result = mvc.post().uri("/api/polls").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"ランチ\"}").exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        Number id = (Number) com.jayway.jsonpath.JsonPath.read(body(result), "$.id");
        return new Host(cookie, id.longValue());
    }

    // ---- 約束1: 別のサイトから、ログイン中の人の名前で書き込めない（CSRF） ----

    @Test
    void CSRFトークンのない書き込みは拒否される() {
        Host host = newHostWithPoll();

        // 別サイトのフォームからの送信を模す: ブラウザは Cookie を自動で付けるが、CSRF トークンは付けられない
        MvcTestResult result = mvc.post().uri("/api/polls/{id}/close", host.pollId())
                .cookie(host.cookie()).exchange();

        System.out.printf("[約束1] Cookie だけで締切を実行 → HTTP %d%n", result.getResponse().getStatus());
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---- 約束2: 知らないオリジンに、Cookie つきの通信を許さない（CORS） ----

    @Test
    void 見知らぬオリジンにCookieつき通信を許可しない() {
        Host host = newHostWithPoll();

        MvcTestResult result = mvc.options().uri("/api/polls/{id}/close", host.pollId())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange();

        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
        String allowCredentials = result.getResponse().getHeader("Access-Control-Allow-Credentials");
        System.out.printf("[約束2] プリフライト Origin=evil.example → Allow-Origin=%s / Allow-Credentials=%s%n",
                allowOrigin, allowCredentials);
        assertThat(allowOrigin).isNotEqualTo("https://evil.example");
    }

    // ---- 約束3: 参加していない投票は読めない・消せない（IDOR）。存在も漏らさない ----

    @Test
    void 参加していない人は他人の投票を読めない() {
        Host host = newHostWithPoll();
        Host stranger = newHostWithPoll();      // 別の訪問者（自分の投票は持っている）

        MvcTestResult result = mvc.get().uri("/api/polls/{id}", host.pollId()).cookie(stranger.cookie()).exchange();

        System.out.printf("[約束3] 他人の投票を GET → HTTP %d%n", result.getResponse().getStatus());
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 参加していない人は他人の投票を消せない() {
        Host host = newHostWithPoll();
        Host stranger = newHostWithPoll();

        MvcTestResult deleted = mvc.delete().uri("/api/polls/{id}", host.pollId())
                .cookie(stranger.cookie()).with(csrf()).exchange();
        MvcTestResult afterwards = mvc.get().uri("/api/polls/{id}", host.pollId()).cookie(host.cookie()).exchange();

        System.out.printf("[約束3] 他人の投票を DELETE → HTTP %d / そのあと主催者が GET → HTTP %d%n",
                deleted.getResponse().getStatus(), afterwards.getResponse().getStatus());
        assertThat(afterwards).hasStatusOk();
    }

    // ---- 約束4: Cookie の値を推測しても、他人になりすませない ----

    @Test
    void 推測したCookieで主催者になりすませない() {
        Host host = newHostWithPoll();

        int accepted = 0;
        for (int guess = 1; guess <= 30; guess++) {
            MvcTestResult result = mvc.post().uri("/api/polls/{id}/close", host.pollId())
                    .cookie(new Cookie(COOKIE, String.valueOf(guess))).with(csrf()).exchange();
            if (result.getResponse().getStatus() == HttpStatus.NO_CONTENT.value()) {
                accepted++;
            }
        }
        System.out.printf("[約束4] Cookie=1..30 を総当たり → 主催者として受理された回数 %d%n", accepted);
        assertThat(accepted).isZero();
    }

    // ---- 約束5: 招待トークンをログに出さない ----

    @Test
    void 招待トークンがログに出ない(CapturedOutput output) {
        Host host = newHostWithPoll();
        MvcTestResult issued = mvc.post().uri("/api/polls/{id}/invites", host.pollId())
                .cookie(host.cookie()).with(csrf()).exchange();
        String token = com.jayway.jsonpath.JsonPath.read(body(issued), "$.token");

        MvcTestResult accepted = mvc.post().uri("/api/invites/{token}/accept", token).with(csrf()).exchange();

        assertThat(accepted).hasStatusOk();
        boolean leaked = output.getAll().contains(token);
        System.out.printf("[約束5] 招待トークンはログに %s%n", leaked ? "出ていた" : "出ていない");
        assertThat(output.getAll()).doesNotContain(token);
    }
}
