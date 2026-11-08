package com.example.quickvote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.example.quickvote.event.PollEventHub;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.vote.VoteService;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実際に Tomcat を起動して、本物のHTTP（JDK の HttpClient）でつなぐ。MockMvc では確かめられないもの:
 * 実サーバーが返す Cookie 属性、CSRF の Cookie ＋ヘッダーの往復、SSE のストリーミングと Last-Event-ID の再送。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RealHttpIT extends AbstractIntegrationTest {

    /** connect() が返すキューの末尾に入る目印。ストリームが終わった（サーバーが閉じた・切断された）ことを表す。 */
    private static final String END_OF_STREAM = "<<end of stream>>";

    @LocalServerPort
    int port;
    @Autowired
    PollEventHub hub;
    @Autowired
    VoteService voteService;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Stream<String>> openStreams = new java.util.ArrayList<>();

    @AfterEach
    void closeStreams() {
        openStreams.forEach(Stream::close);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieNamed(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "="))
                .findFirst().orElseThrow(() -> new AssertionError(name + " の Set-Cookie がない"));
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    @Test
    void CSRFのCookieとヘッダーの往復_実サーバーのCookie属性_セキュリティヘッダー() throws Exception {
        // 1. 最初の GET で XSRF-TOKEN の Cookie が発行される（JSから読めるので HttpOnly ではない）
        HttpResponse<String> first = send(HttpRequest.newBuilder(uri("/actuator/health")));
        String xsrfCookie = cookieNamed(first, "XSRF-TOKEN");
        assertThat(xsrfCookie).contains("Path=/", "SameSite=Lax").doesNotContain("HttpOnly").doesNotContain("Secure");
        String xsrf = cookieValue(xsrfCookie);
        assertThat(first.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                v -> assertThat(v).contains("default-src 'self'"));
        assertThat(first.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(first.headers().firstValue("X-Frame-Options")).hasValue("DENY");

        String body = "{\"title\":\"実HTTP\",\"closesInMinutes\":60}";
        HttpRequest.Builder post = HttpRequest.newBuilder(uri("/api/polls"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));

        // 2. ヘッダーなし → 403（Cookie だけでは通らない。ブラウザが自動で付ける Cookie だけで更新できてはいけない）
        HttpResponse<String> noHeader = send(post.copy().header("Cookie", "XSRF-TOKEN=" + xsrf));
        assertThat(noHeader.statusCode()).isEqualTo(403);
        assertThat(noHeader.body()).contains("CSRF_REJECTED");
        // 3. ヘッダーの値が違う → 403
        HttpResponse<String> wrong = send(post.copy().header("Cookie", "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", "forged"));
        assertThat(wrong.statusCode()).isEqualTo(403);
        // 4. Cookie と同じ値をヘッダーに入れる → 201。同時に訪問者 Cookie が発行される
        HttpResponse<String> ok = send(post.copy().header("Cookie", "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf));
        assertThat(ok.statusCode()).isEqualTo(201);
        assertThat(cookieNamed(ok, "QV_VISITOR")).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=2592000")
                .doesNotContain("Secure");
        assertThat(ok.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(ok.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void 独自フィルタなしで_どんな最初のGETにも_XSRF_TOKENのCookieが発行される() throws Exception {
        // csrf.spa() は CSRF トークンを遅延させず、CsrfFilter が読み込む。トークンを読むための自作フィルタは要らない。
        // 静的ファイル・Actuator・認証が要るAPI（401）・存在しないパス（404 / 拒否）のどれでも、最初の GET で Cookie が出る。
        for (String path : new String[] { "/", "/index.html", "/app.js", "/actuator/health",
                "/api/polls/3f2504e0-4f89-41d3-9a0c-0305e82c3301", "/no-such-path" }) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path)));

            assertThat(response.headers().allValues("Set-Cookie")).as(path + " (HTTP " + response.statusCode() + ")")
                    .anyMatch(c -> c.startsWith("XSRF-TOKEN="));
        }
    }

    @Test
    void 静的クライアントが配信される() throws Exception {
        HttpResponse<String> index = send(HttpRequest.newBuilder(uri("/")));
        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.body()).contains("QuickVote");
        assertThat(index.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("XSRF-TOKEN="));
        assertThat(send(HttpRequest.newBuilder(uri("/app.js"))).statusCode()).isEqualTo(200);
        assertThat(send(HttpRequest.newBuilder(uri("/app.css"))).statusCode()).isEqualTo(200);
        // 許可リストにないパスは静的ファイルであっても配信しない
        assertThat(send(HttpRequest.newBuilder(uri("/other.txt"))).statusCode()).isIn(401, 403, 404);
    }

    // ---- SSE ----

    /** SSE のストリームを読み、行ごとにキューへ入れる。 */
    private BlockingQueue<String> connect(TestVisitor visitor, String lastEventId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(pathOfOnlyPoll() + "/events"))
                .header("Accept", "text/event-stream").header("Cookie", "QV_VISITOR=" + visitor.token());
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<Stream<String>> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        openStreams.add(response.body());
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread.ofPlatform().daemon().start(() -> {
            try {
                response.body().forEach(lines::add);
            } catch (RuntimeException e) {
                // 接続を閉じたときの終了。テストの検証対象ではない
            }
            lines.add(END_OF_STREAM);
        });
        return lines;
    }

    private String pathOfOnlyPoll() {
        return "/api/polls/" + jdbc.sql("SELECT public_id FROM polls").query(String.class).single();
    }

    private static String nextNonEmptyLine(BlockingQueue<String> lines) throws InterruptedException {
        while (true) {
            String line = lines.poll(10, TimeUnit.SECONDS);
            assertThat(line).as("10秒以内にSSEの行が届くこと").isNotNull();
            if (!line.isEmpty() && !line.startsWith("retry")) {
                return line;
            }
        }
    }

    /** 空行までを1イベントとして読む（コメント行は含めない）。 */
    private static Map<String, String> nextEvent(BlockingQueue<String> lines) throws InterruptedException {
        Map<String, String> event = new LinkedHashMap<>();
        while (true) {
            String line = lines.poll(10, TimeUnit.SECONDS);
            assertThat(line).as("10秒以内にSSEの行が届くこと").isNotNull();
            if (line.isEmpty()) {
                if (!event.isEmpty()) {
                    return event;
                }
                continue;
            }
            if (line.startsWith(":") || line.startsWith("retry:")) {
                continue; // コメント行と再接続間隔の指定はイベントではない
            }
            int colon = line.indexOf(':');
            event.put(line.substring(0, colon), line.substring(colon + 1).strip());
        }
    }

    @Test
    void SSEで得票がリアルタイムに届き_再接続ではLast_Event_IDより後が再送される() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B");
        TestVisitor member = newMember(poll); // seq 1: PARTICIPANT_JOINED（接続前）
        long optionA = poll.options().get(0).id();
        long optionB = poll.options().get(1).id();

        BlockingQueue<String> first = connect(member, null);
        voteService.vote(member.id(), poll.id(), optionA); // seq 2（接続中に届く）
        Map<String, String> live = nextEvent(first);
        assertThat(live).containsEntry("id", "2").containsEntry("event", "VOTE_CAST");
        // payload は jsonb として保存されるため、PostgreSQL が整形した形（"optionId": 1）で届く
        assertThat(live.get("data")).containsPattern("\"optionId\":\\s*" + optionA + "\\b");

        // 回線が切れた状況: 接続を閉じる。その間に別の変更（seq 3）が起きる。
        openStreams.forEach(Stream::close);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            hub.sendHeartbeats(); // 送信に失敗した購読者を名簿から外す
            return hub.subscriberCount() == 0;
        });
        voteService.vote(member.id(), poll.id(), optionB);

        // 再接続（ブラウザの EventSource が自動で行うこと）: 最後に受け取った id=2 を Last-Event-ID で送る
        BlockingQueue<String> second = connect(member, "2");
        Map<String, String> replayed = nextEvent(second);
        assertThat(replayed).containsEntry("id", "3").containsEntry("event", "VOTE_CAST");
        assertThat(replayed.get("data")).containsPattern("\"optionId\":\\s*" + optionB + "\\b");

        // 再送のあとは通常のライブ配信に戻る（重複なし: 次は seq 4）
        pollService.close(host.id(), poll.id());
        assertThat(nextEvent(second)).containsEntry("id", "4").containsEntry("event", "POLL_CLOSED");
    }

    @Test
    void パーセントエンコードしたURLでも_実サーバーで参加コードの試行回数が制限される() throws Exception {
        HttpResponse<String> first = send(HttpRequest.newBuilder(uri("/")));
        String xsrf = cookieValue(cookieNamed(first, "XSRF-TOKEN"));
        TestVisitor visitor = newVisitor();
        HttpRequest.Builder join = HttpRequest.newBuilder(uri("/api/%6aoin")) // %6a = j。MVC は /api/join として処理する
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrf + "; QV_VISITOR=" + visitor.token())
                .header("X-XSRF-TOKEN", xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"ABCDEFGH\"}"));

        for (int i = 0; i < 10; i++) {
            HttpResponse<String> attempt = send(join.copy());
            assertThat(attempt.statusCode()).isEqualTo(404);
            assertThat(attempt.body()).contains("JOIN_CODE_NOT_FOUND"); // /api/join として処理された
        }

        HttpResponse<String> limited = send(join.copy());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void SSEの接続をサーバーが閉じても_認可の再評価でエラーが出ない(CapturedOutput output) throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        BlockingQueue<String> lines = connect(host, null);
        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo(":connected");

        // サーバー側から終わらせる（接続のタイムアウトやサーバーの停止と同じ。emitter.complete() → ASYNC ディスパッチ）
        hub.closeAll();
        assertThat(awaitEndOfStream(lines)).isTrue();
        Thread.sleep(1000); // 非同期のディスパッチがフィルタチェーンを通り終わるのを待つ

        // 以前は、ASYNC ディスパッチで認可が再評価されて認証が失われ、
        // AuthorizationDeniedException と「応答はコミット済み」のエラーが ERROR で出ていた。
        assertThat(output.getAll()).doesNotContain("AuthorizationDeniedException")
                .doesNotContain("already been committed").doesNotContain("Servlet.service()");
        assertThat(output.getAll()).doesNotContain("\"level\":\"ERROR\"");
    }

    @Test
    void SSEの接続をクライアントが切っても_認可の再評価でエラーが出ない(CapturedOutput output) throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        BlockingQueue<String> lines = connect(host, null);
        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo(":connected");

        openStreams.forEach(Stream::close); // クライアントが切断
        assertThat(awaitEndOfStream(lines)).isTrue();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            hub.sendHeartbeats(); // 切れた接続への書き込みが失敗し、名簿から外れる
            return hub.subscriberCount() == 0;
        });
        Thread.sleep(1000);

        assertThat(output.getAll()).doesNotContain("AuthorizationDeniedException")
                .doesNotContain("already been committed");
        assertThat(output.getAll()).doesNotContain("\"level\":\"ERROR\"");
    }

    private static boolean awaitEndOfStream(BlockingQueue<String> lines) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(1, TimeUnit.SECONDS);
            if (END_OF_STREAM.equals(line)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void ハートビートのコメント行が送られる() throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");

        BlockingQueue<String> lines = connect(host, null);
        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo(":connected"); // 接続直後に送るコメント行
        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo("retry:3000");
        hub.sendHeartbeats();

        assertThat(nextNonEmptyLine(lines)).isEqualTo(":heartbeat");
    }

    @Test
    void SSEの購読は参加者だけ_非参加者は404_Cookieなしは401() throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        TestVisitor outsider = newVisitor();
        HttpRequest.Builder base = HttpRequest.newBuilder(uri(pathOfOnlyPoll() + "/events"))
                .header("Accept", "text/event-stream");

        HttpResponse<String> notParticipant = send(base.copy().header("Cookie", "QV_VISITOR=" + outsider.token()));
        HttpResponse<String> anonymous = send(base.copy());

        assertThat(notParticipant.statusCode()).isEqualTo(404);
        assertThat(notParticipant.body()).contains("POLL_NOT_FOUND");
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    @Test
    void Last_Event_IDが数値でなくても接続できる() throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");

        BlockingQueue<String> lines = connect(host, "not-a-number");
        hub.sendHeartbeats();

        assertThat(nextNonEmptyLine(lines)).isEqualTo(":connected");
    }
}
