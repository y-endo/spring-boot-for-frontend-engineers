package com.example.quickvote;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Actuator は health（liveness/readiness）と metrics だけを公開し、それ以外は閉じている。 */
class ActuatorIT extends AbstractIntegrationTest {

    @Test
    void healthとliveness_readinessは認証なしで見られ_詳細は出さない() {
        for (String path : new String[] { "/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness" }) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertThat(result).as(path).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
            assertThat(result).as(path).bodyText().doesNotContain("components"); // show-details: never
        }
    }

    @Test
    void metricsは取得できる() {
        assertThat(mvc.get().uri("/actuator/metrics").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/metrics/jvm.memory.used").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/metrics/hikaricp.connections.active").exchange()).hasStatusOk();
    }

    @Test
    void それ以外のエンドポイントは公開されていない() {
        for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/heapdump",
                "/actuator/configprops", "/actuator/loggers", "/actuator" }) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertThat(result.getResponse().getStatus()).as(path)
                    .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
        }
    }
}
