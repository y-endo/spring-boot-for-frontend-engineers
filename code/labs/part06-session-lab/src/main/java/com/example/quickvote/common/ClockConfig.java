package com.example.quickvote.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * 現在時刻は Instant.now() を直接呼ばず、この Clock 経由で取る。
     * テストでは差し替えて時間を進められる（締切・招待期限・冪等性の保持期限など）。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
