package com.example.securitymap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 確認問題用：anyRequest() の後ろに規則を書くと、どうなるか。 */
@Configuration
@Profile("badorder")
public class BadOrderConfig {

    @Bean
    SecurityFilterChain badOrder(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .anyRequest().authenticated()
                .requestMatchers("/hello").permitAll());
        return http.build();
    }
}
