package com.example.loginauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 「Googleでログイン」型。ログインを外部の認可サーバー（OP）に任せる（クライアント側）。 */
@Configuration
@Profile("oauth2login")
class OAuth2LoginConfig {

    @Bean
    SecurityFilterChain oauthChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults());
        return http.build();
    }
}
