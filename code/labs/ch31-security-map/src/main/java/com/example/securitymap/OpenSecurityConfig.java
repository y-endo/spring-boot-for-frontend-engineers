package com.example.securitymap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 段階1：全部許可。Spring Security を入れたのに、何も守られていない状態を観察する。 */
@Configuration
@Profile("open")
public class OpenSecurityConfig {

    @Bean
    SecurityFilterChain open(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
