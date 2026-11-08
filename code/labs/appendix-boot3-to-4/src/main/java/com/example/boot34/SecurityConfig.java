package com.example.boot34;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Boot 3.0 以前: WebSecurityConfigurerAdapter を extends して configure(HttpSecurity) を override
// いま: SecurityFilterChain の Bean を返す。設定はラムダ（and() でつなぐ書き方は無い）
@Configuration
@EnableWebSecurity
@EnableMethodSecurity                      // 旧: @EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.spa())                                   // 旧: http.csrf().disable().and()...
            .authorizeHttpRequests(a -> a                              // 旧: authorizeRequests().antMatchers(...)
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
