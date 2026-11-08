package com.example.securitymap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 段階2：許可するものを列挙し、それ以外は全部拒否する（許可リスト方式）。 */
@Configuration
@Profile("closed")
public class ClosedSecurityConfig {

    @Bean
    SecurityFilterChain closed(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/hello").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"code\":\"FORBIDDEN\"}");
                        }))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable());
        return http.build();
    }
}
