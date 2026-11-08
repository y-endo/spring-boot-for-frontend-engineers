package com.example.quickvote.security;

import java.time.Clock;

import com.example.quickvote.common.QuickVoteProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security 7 の設定（ラムダDSL）。
 * <p>
 * フィルタの並び（このアプリで関係するもの）:
 * <pre>
 * CsrfFilter → RateLimitFilter → VisitorAuthenticationFilter → AnonymousAuthenticationFilter
 *   → AuthorizationFilter（authorizeHttpRequests の判定）
 * </pre>
 * 自作フィルタは @Component にしない。Bean にすると Spring Boot が Servlet フィルタとしても登録し、
 * 同じフィルタが2回走ってしまう。SecurityFilterChain の中で new して登録する。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, VisitorRepository visitors, Clock clock,
            QuickVoteProperties properties, RateLimiter rateLimiter,
            ProblemDetailSecurityHandler problemHandler,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {

        // CSRFトークンの Cookie（XSRF-TOKEN）。JS が読んで X-XSRF-TOKEN ヘッダーに入れるので HttpOnly にはしない。
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(properties.visitorCookie().secure())
                .sameSite("Lax"));

        // SecurityContext の保存先。セッションではなくリクエストの属性（そのリクエストの間だけ）。
        // 認証フィルタ（VisitorAuthenticationFilter）がここへ保存し、SecurityContextHolderFilter が、
        // 同じリクエストの別のディスパッチ（SSE が完了するときの ASYNC ディスパッチなど）で読み戻す。
        RequestAttributeSecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

        http
                // サーバー側にセッションを持たない。毎回 Cookie の訪問者トークンから利用主体を特定する。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // SPA向けのCSRF設定。csrf.spa() が Spring Security 7 の公式推奨の組み合わせ
                // （Cookie でトークンを渡し、ヘッダー X-XSRF-TOKEN で受け取る）。
                // 最初の GET の応答にも XSRF-TOKEN の Cookie が載る（トークンを遅延させず、CsrfFilter が読み込むため）。
                // Cookie の Secure/SameSite だけ設定で切り替えたいので、リポジトリを差し替えている。
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
                // ログイン画面・Basic認証・ログアウトは使わない（匿名の訪問者のみ）。
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // 静的クライアント
                        .requestMatchers("/", "/index.html", "/app.js", "/app.css", "/favicon.ico", "/error").permitAll()
                        // 公開する Actuator は health と metrics だけ（application.yaml で他は公開していない）
                        .requestMatchers(EndpointRequest.to("health", "metrics")).permitAll()
                        // API は訪問者として識別できていること。投票ごとの権限は各サービスが参加者表で判定する。
                        .requestMatchers("/api/**").authenticated()
                        // 上に書いていないものはすべて拒否（許可リスト方式）
                        .anyRequest().denyAll())
                // 回数制限は訪問者の発行より前（拒否される前に visitors の行を作らせない）。CSRF の検査のあと。
                .addFilterAfter(new RateLimitFilter(rateLimiter, resolver), CsrfFilter.class)
                .addFilterBefore(new VisitorAuthenticationFilter(visitors, clock, properties, contextRepository),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
