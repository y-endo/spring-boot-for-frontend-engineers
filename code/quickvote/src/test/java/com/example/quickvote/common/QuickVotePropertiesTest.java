package com.example.quickvote.common;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 設定値の検証。「不正な値は起動時に落とす」ことを、実際に起動（コンテキストの作成）の失敗で確かめる。
 * Spring Boot 全体は起動せず、設定クラスだけを読み込む ApplicationContextRunner を使う。
 */
class QuickVotePropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(QuickVoteProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    private static String messages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void 何も設定しなければ既定値で起動する() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            QuickVoteProperties properties = context.getBean(QuickVoteProperties.class);
            assertThat(properties.idempotency().retention()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.sse().timeout()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.sse().heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.sse().sendTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.sse().deliveryThreads()).isEqualTo(4);
            assertThat(properties.cleanup().interval()).isEqualTo(Duration.ofMinutes(5));
        });
    }

    @ParameterizedTest(name = "{0}={1} は起動時に拒否される")
    @CsvSource({
            // 冪等性の保持期間: 0 だと記録がすぐ期限切れになり、再送が二重に実行される
            "quickvote.idempotency.retention, 0s",
            "quickvote.idempotency.retention, -1h",
            "quickvote.idempotency.retention, 30s",
            "quickvote.idempotency.retention, 8d",
            // SSE
            "quickvote.sse.timeout, 0s",
            "quickvote.sse.timeout, -30m",
            "quickvote.sse.timeout, 25h",
            "quickvote.sse.heartbeat-interval, 0s",
            "quickvote.sse.heartbeat-interval, -15s",
            "quickvote.sse.heartbeat-interval, 10m",
            "quickvote.sse.send-timeout, 0s",
            "quickvote.sse.send-timeout, 10m",
            "quickvote.sse.delivery-threads, 0",
            "quickvote.sse.delivery-threads, -1",
            "quickvote.sse.delivery-threads, 65",
            // 期限切れ削除
            "quickvote.cleanup.interval, 0s",
            "quickvote.cleanup.interval, 5s",
            "quickvote.cleanup.interval, 2d",
            "quickvote.cleanup.batch-size, 0",
            "quickvote.cleanup.max-batches-per-run, 0",
            // 回数制限
            "quickvote.rate-limit.ip-capacity, 0",
            "quickvote.rate-limit.visitor-refill-per-minute, 0",
            "quickvote.rate-limit.create-capacity, -1",
    })
    void 範囲外の設定は起動時に落ちて_どの設定が悪いかが分かる(String key, String value) {
        runner.withPropertyValues(key + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            String property = key.replace("-", "").toLowerCase();
            assertThat(messages(context.getStartupFailure()).replace("-", "").toLowerCase()).contains(property);
        });
    }

    @ParameterizedTest(name = "{0}={1} は許される")
    @CsvSource({
            "quickvote.idempotency.retention, 1m",
            "quickvote.idempotency.retention, 7d",
            "quickvote.sse.timeout, 1m",
            "quickvote.sse.timeout, 24h",
            "quickvote.sse.heartbeat-interval, 1s",
            "quickvote.sse.heartbeat-interval, 5m",
            "quickvote.sse.delivery-threads, 1",
            "quickvote.sse.delivery-threads, 64",
            "quickvote.cleanup.interval, 10s",
            "quickvote.cleanup.interval, 1d",
    })
    void 範囲の境界の値は受け付ける(String key, String value) {
        runner.withPropertyValues(key + "=" + value).run(context -> assertThat(context).hasNotFailed());
    }
}
