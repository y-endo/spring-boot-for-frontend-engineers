package com.example.quickvote.security;

import java.util.List;

import com.example.quickvote.common.QuickVoteProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 完成形（code/quickvote）の SecurityConfig に、実験用のスイッチ（lab.*）を足した版。
 * スイッチを既定のままにすれば、完成形と同じ構成になる。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, VisitorRepository visitors,
            QuickVoteProperties properties, LabProperties lab, RateLimiter rateLimiter,
            ProblemDetailSecurityHandler problemHandler,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(properties.visitorCookie().secure())
                .sameSite("Lax"));

        RequestAttributeSecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/victim.html", "/victim.js", "/favicon.ico", "/error", "/lab/stats").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());

        if (!lab.sessionManagement()) {
            http.sessionManagement(AbstractHttpConfigurer::disable); // 第32章の実験用
        }

        if (lab.csrf()) {
            http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository));
        } else {
            http.csrf(AbstractHttpConfigurer::disable); // 第34章の実験用。本番でやってはいけない
        }

        if (lab.corsAllowOrigin().isBlank()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else {
            http.cors(cors -> cors.configurationSource(corsSource(lab.corsAllowOrigin())));
        }

        if (!"none".equals(lab.rateLimit())) {
            // 回数制限は訪問者の発行より前。CSRF を無効にした実験では、CsrfFilter が並ばないので AnonymousAuthenticationFilter の前に置く。
            RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, resolver, "raw-uri".equals(lab.rateLimit()));
            if (lab.csrf()) {
                http.addFilterAfter(rateLimitFilter, CsrfFilter.class);
            } else {
                http.addFilterBefore(rateLimitFilter, AnonymousAuthenticationFilter.class);
            }
        }
        http.addFilterBefore(
                new VisitorAuthenticationFilter(visitors, properties, contextRepository, lab.saveContext()),
                AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** CORS：指定した1つのオリジンだけを、Cookie つきで許可する。"*" と credentials の組み合わせは禁止されている。 */
    private static CorsConfigurationSource corsSource(String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
