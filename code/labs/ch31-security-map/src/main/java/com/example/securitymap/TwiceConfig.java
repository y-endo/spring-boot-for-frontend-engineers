package com.example.securitymap;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 段階3：自作フィルタを @Component にすると何回動くか。
 * PlainFilter は Filter を直接実装、OnceFilter は OncePerRequestFilter を継承。どちらも @Component なし/ありで比べる。
 */
@Configuration
@Profile("twice")
public class TwiceConfig {

    static final Logger log = LoggerFactory.getLogger("twice");

    /** Bean にしてしまった例（Spring Boot が Servlet フィルタとしても登録する）。 */
    @Component
    static class PlainFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            log.info("PlainFilter 通過: {}", ((HttpServletRequest) req).getRequestURI());
            chain.doFilter(req, res);
        }
    }

    @Component
    static class OnceFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            log.info("OnceFilter 通過: {}", req.getRequestURI());
            chain.doFilter(req, res);
        }
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, PlainFilter plain, OnceFilter once) throws Exception {
        http
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(plain, AnonymousAuthenticationFilter.class)
                .addFilterBefore(once, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
