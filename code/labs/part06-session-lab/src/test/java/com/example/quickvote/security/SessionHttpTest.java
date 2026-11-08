package com.example.quickvote.security;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第32章：実 Tomcat に HTTP で当てるテスト。Cookie の発行、入口以外では発行しないこと、
 * SSE の完了（非同期ディスパッチ）でログに ERROR が出ないこと。
 * 実験用スイッチ lab.session-management=false と lab.save-context=false を組み合わせて起動すると、
 * SSE のテストが赤くなる（第32章「壊してみる」）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "lab.rate-limit=none")
@ExtendWith(OutputCaptureExtension.class)
class SessionHttpTest {

    @LocalServerPort
    int port;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst()
                .ifPresent(c -> builder.header("X-XSRF-TOKEN", c.getValue()));
        if (json != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final String POLL = "{\"title\":\"t\",\"closesInMinutes\":30,\"allowMemberOptions\":false,\"options\":[\"A\"]}";

    @Test
    void 入口のPOSTだけが訪問者Cookieを発行し_属性が付く() throws Exception {
        assertThat(send("GET", "/victim.html", null).statusCode()).isEqualTo(200);
        var noCookie = send("GET", "/api/polls/00000000-0000-4000-8000-000000000000", null);
        assertThat(noCookie.statusCode()).isEqualTo(401);
        assertThat(noCookie.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("QV_VISITOR"));

        var created = send("POST", "/api/polls", POLL);

        assertThat(created.statusCode()).isEqualTo(201);
        List<String> setCookies = created.headers().allValues("Set-Cookie");
        String visitor = setCookies.stream().filter(c -> c.startsWith("QV_VISITOR=")).findFirst().orElseThrow();
        assertThat(visitor).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").contains("Max-Age=2592000");
        assertThat(visitor.split(";")[0].substring("QV_VISITOR=".length())).hasSize(43);
    }

    @Test
    void SSEが完了しても_ログにERRORが出ない(CapturedOutput output) throws Exception {
        send("GET", "/victim.html", null);
        String body = send("POST", "/api/polls", POLL).body();
        String id = body.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");

        var sse = send("GET", "/api/polls/" + id + "/events", null);
        Thread.sleep(1000); // 0.3 秒後の complete() と、その後の非同期ディスパッチを待つ

        assertThat(sse.statusCode()).isEqualTo(200);
        assertThat(output.getAll()).doesNotContain("AuthorizationDeniedException").doesNotContain(" ERROR ");
    }
}
