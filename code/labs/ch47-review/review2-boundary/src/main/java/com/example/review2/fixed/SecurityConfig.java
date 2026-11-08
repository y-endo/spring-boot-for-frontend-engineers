package com.example.review2.fixed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 認証は Cookie（自前のフィルタ）なので、CSRF 対策は必須。SPA 向けの設定（csrf.spa()）にする。
     * CORS は設定しない（同一オリジンのクライアントだけ）。許可する相手が増えたら、オリジンを列挙する。
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());   // 認可は PollController で投票ごとに判定する
        return http.build();
    }
}
