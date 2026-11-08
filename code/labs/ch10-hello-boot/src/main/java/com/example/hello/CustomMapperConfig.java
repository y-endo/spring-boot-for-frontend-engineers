package com.example.hello;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 実験用。--spring.profiles.active=custom-mapper のときだけ有効になり、自動構成の JsonMapper を押しのける。 */
@Configuration
@Profile("custom-mapper")
public class CustomMapperConfig {

    @Bean
    public JsonMapper myMapper() {
        return JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    }
}
