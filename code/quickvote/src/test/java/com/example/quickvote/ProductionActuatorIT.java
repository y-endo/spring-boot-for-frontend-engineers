package com.example.quickvote;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本番プロファイル（prod）では、Actuator は管理ポートだけで提供され、利用者向けのポートからは見えない。
 * 本番の管理ポートは 9090 だが、テストでは他と衝突しないよう、ランダムなポート（0）に差し替えている。
 * 管理ポートを別にする設定そのもの（application-prod.yaml の management.server.port）が有効なことを確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("prod")
class ProductionActuatorIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;
    @Value("${local.management.port}")
    int managementPort;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(int onPort, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 管理ポートは利用者向けのポートとは別() {
        assertThat(managementPort).isNotEqualTo(port);
    }

    @Test
    void 利用者向けのポートでは_healthもmetricsも見えない() throws Exception {
        for (String path : new String[] { "/actuator/metrics", "/actuator/metrics/jvm.memory.used",
                "/actuator/metrics/hikaricp.connections.active", "/actuator/health", "/actuator/health/readiness" }) {
            HttpResponse<String> response = get(port, path);

            assertThat(response.statusCode()).as(path).isIn(401, 403, 404);
            assertThat(response.body()).as(path).doesNotContain("jvm.memory").doesNotContain("\"status\":\"UP\"");
        }
    }

    @Test
    void 管理ポートでは_healthとmetricsだけが見える() throws Exception {
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/metrics/jvm.memory.used").statusCode()).isEqualTo(200);
        for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/heapdump",
                "/actuator/configprops", "/actuator/loggers" }) {
            assertThat(get(managementPort, path).statusCode()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void 本番プロファイルでは_訪問者CookieにSecureが付く() throws Exception {
        // prod の設定が読み込まれていることの確認（quickvote.visitor-cookie.secure=true）
        HttpResponse<String> response = get(port, "/");

        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("XSRF-TOKEN=")
                && c.contains("Secure"));
    }
}
