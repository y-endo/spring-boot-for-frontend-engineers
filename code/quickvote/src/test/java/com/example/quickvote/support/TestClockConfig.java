package com.example.quickvote.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * テスト用の Clock を差し込む設定。本番の Clock Bean（ClockConfig）より優先される。
 * テストから advance() で時間を進められる。
 * テストクラスの入れ子にせず、独立したクラスにして @Import する
 * （入れ子の @TestConfiguration は「既定の設定クラス」と見なされ、Spring Framework 7.1 で挙動が変わる警告が出る）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(Instant.now());
    }
}
