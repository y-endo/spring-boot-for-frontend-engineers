package com.example.quickvote;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * readiness は DB に届くかどうかを見る。
 * DB のコンテナを実際に止めるので、ほかのテストとコンテキスト（とコンテナ）を共有しないようにする
 * （marker のプロパティでコンテキストを分け、終わったらコンテキストを破棄する）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "readiness-it.marker=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;
    @Autowired
    PostgreSQLContainer container;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void DBが止まるとreadinessはDOWNになる_livenessはUPのまま() throws Exception {
        // DB に届いているあいだは UP
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);

        container.stop();

        // DB に届かなくなったら、API が失敗している状態で「準備完了」と答えてはいけない（503 / DOWN）
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            HttpResponse<String> readiness = get("/actuator/health/readiness");
            assertThat(readiness.statusCode()).isEqualTo(503);
            assertThat(readiness.body()).contains("\"status\":\"DOWN\"").doesNotContain("components");
        });
        // liveness は DB を含めない。DB の障害でアプリを再起動しても直らないので、再起動の判断に使わせない。
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    }
}
