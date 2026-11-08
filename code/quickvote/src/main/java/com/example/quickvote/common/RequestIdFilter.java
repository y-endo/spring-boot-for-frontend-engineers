package com.example.quickvote.common;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * リクエストごとの相関ID。X-Request-Id を受け取る（なければ採番）→ MDC に入れる → 応答ヘッダーに返す。
 * MDC に入れた値は、そのリクエストを処理するスレッドのすべてのログ行に自動で付く。
 * <p>
 * アクセスログ代わりに、リクエストの最後に1行だけ出す。出すのは許可した項目だけ:
 * メソッド・ルートのパターン・ステータス・所要時間。実際のURIやクエリは出さない
 * （招待トークンがパスに入っているため。/api/invites/{token}/accept のようにパターンで出す）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** 外から来た値をそのまま信用しない。ログ汚染（改行など）を防ぐため、形式が合うものだけ使う。 */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route != null ? route.toString() : "unmatched")
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                    .log("request completed");
            // スレッドはプールで使い回されるので、必ず消す。消し忘れると次のリクエストのログに混ざる。
            MDC.remove(MDC_KEY);
        }
    }
}
