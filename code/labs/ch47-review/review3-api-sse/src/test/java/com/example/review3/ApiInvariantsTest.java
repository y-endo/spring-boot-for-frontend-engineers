package com.example.review3;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.review3.infra.Broadcaster;
import com.example.review3.infra.VoteRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 「守る約束」をそのままテストにしたもの（実 Tomcat に HTTP で話しかける）。
 * bad では赤（失敗）になるのが正しい。fixed では緑になる。
 */
abstract class ApiInvariantsTest {

    @LocalServerPort
    int port;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    Broadcaster hub;
    @MockitoSpyBean
    VoteRecorder recorder;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE polls, options RESTART IDENTITY CASCADE").update();
        jdbc.sql("INSERT INTO polls (title, join_code, host_visitor_id) VALUES ('ランチ', 'K7M2Q9XA', 42)").update();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    // ---- 約束1: API は、出してよい項目だけを返す（DB の行や Entity をそのまま返さない） ----

    @Test
    void 詳細の応答に内部の項目が含まれない() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/polls/1")).GET().build());

        System.out.printf("[約束1] GET /api/polls/1 → %s%n", response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("joinCode", "hostVisitorId", "version", "deleteAfter");
    }

    // ---- 約束2: 入力の誤りは400。内部の情報（SQL・行の中身・スタック）を応答に出さない ----

    @Test
    void 不正な入力は400になり内部情報を漏らさない() throws Exception {
        for (String label : List.of("", "x".repeat(101))) {
            HttpRequest request = HttpRequest.newBuilder(uri("/api/polls/1/options"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"label\":\"" + label + "\"}")).build();
            HttpResponse<String> response = send(request);

            String shown = response.body().length() > 160 ? response.body().substring(0, 160) + "…" : response.body();
            System.out.printf("[約束2] label=%d文字 → HTTP %d %s%n", label.length(), response.statusCode(), shown);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("Failing row", "INSERT", "org.postgresql", ".java");
        }
    }

    // ---- 約束3: 切れた SSE 接続は、名簿から外れる（接続が増え続けない） ----

    @Test
    void 切断したSSEの購読者は名簿から外れる() throws Exception {
        int before = hub.subscriberCount();
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Socket socket = new Socket("localhost", port);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /api/polls/1/events HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            sockets.add(socket);
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> hub.subscriberCount() == before + 5);

        for (Socket socket : sockets) {                    // ブラウザのタブを閉じた状態
            socket.close();
        }
        // 切断はこちらから書き込んでみて初めて分かる（ハートビートの代わりに ping を送る）
        long deadline = System.currentTimeMillis() + 5000;
        while (hub.subscriberCount() > before && System.currentTimeMillis() < deadline) {
            hub.broadcast("ping");
            Thread.sleep(100);
        }
        System.out.printf("[約束3] 5本接続して全部切断 → 5秒後の購読者数 %d（接続前 %d）%n", hub.subscriberCount(), before);
        assertThat(hub.subscriberCount()).isEqualTo(before);
    }

    // ---- 約束4: 共有される Bean に、リクエスト固有の値を置かない ----

    @Test
    void 同時に来た別人の投票が混ざらない() throws Exception {
        CountDownLatch aInside = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {            // 最初に来た alice の処理を、DB 待ちの最中で止める
                aInside.countDown();
                bDone.await(10, TimeUnit.SECONDS);
            }
            return null;
        }).when(recorder).record(anyLong());

        HttpRequest alice = HttpRequest.newBuilder(uri("/api/votes?visitor=alice&optionId=1"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpRequest bob = HttpRequest.newBuilder(uri("/api/votes?visitor=bob&optionId=1"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        CompletableFuture<HttpResponse<String>> aliceResponse = CompletableFuture.supplyAsync(() -> {
            try {
                return send(alice);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(aInside.await(10, TimeUnit.SECONDS)).isTrue();
        send(bob);                                         // alice が待っている間に bob が最後まで処理される
        bDone.countDown();

        String body = aliceResponse.get(10, TimeUnit.SECONDS).body();
        System.out.printf("[約束4] alice の画面に出た応答: %s%n", body);
        assertThat(body).contains("alice").doesNotContain("bob");
    }
}
