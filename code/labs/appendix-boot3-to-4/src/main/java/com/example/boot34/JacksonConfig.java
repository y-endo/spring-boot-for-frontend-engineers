package com.example.boot34;

import org.springframework.boot.jackson.JacksonComponent;                              // 旧: JsonComponent
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;     // 旧: Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.DateTimeFeature;                                     // 旧: SerializationFeature.WRITE_DATES_AS_TIMESTAMPS

@Configuration
class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);   // 例: 数値の時刻に戻したいとき
    }

    @JacksonComponent
    static class Marker {}   // 旧: @JsonComponent。シリアライザ/デシリアライザの置き場（ここでは名前の確認だけ）
}
