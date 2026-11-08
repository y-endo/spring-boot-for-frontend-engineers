package com.example.loginauth;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** API が JWT（Bearer トークン）を検証する側（リソースサーバー）。ログイン画面は持たない。 */
@Configuration
@Profile("resource")
class ResourceServerConfig {

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c -> c.disable())                                     // Cookie を使わない API なら CSRF の前提が変わる（本文参照）
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                .requestMatchers("/api/**").hasAuthority("SCOPE_read")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        // 学習用の共有秘密鍵（HS256）。本番は認可サーバーの公開鍵 (jwk-set-uri / issuer-uri) を使う
        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
