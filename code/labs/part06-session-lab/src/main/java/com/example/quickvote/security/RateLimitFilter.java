package com.example.quickvote.security;

import java.io.IOException;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.Tokens;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

/**
 * 試行回数を数え、超えたら 429 ＋ Retry-After を返すフィルタ。完成形（code/quickvote）と同じ構造。
 * 違いは、実験用スイッチ lab.rate-limit で「対象かどうかの判定方法」を選べること。
 * <ul>
 * <li>path-pattern: 完成形。PathPatternRequestMatcher（MVC と同じ、デコード後のパスで判定）</li>
 * <li>raw-uri: 元のバグ。getRequestURI() の文字列を比べる。"/api/%6aoin" ですり抜けられる</li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final HandlerExceptionResolver resolver;
    private final boolean rawUri;

    public RateLimitFilter(RateLimiter limiter, HandlerExceptionResolver resolver, boolean rawUri) {
        this.limiter = limiter;
        this.resolver = resolver;
        this.rawUri = rawUri;
    }

    private boolean isAttempt(HttpServletRequest request) {
        if (rawUri) {
            return "POST".equals(request.getMethod()) && "/api/join".equals(request.getRequestURI());
        }
        return EntryMatchers.isAttempt(request);
    }

    private boolean isCreation(HttpServletRequest request) {
        if (rawUri) {
            return "POST".equals(request.getMethod()) && "/api/polls".equals(request.getRequestURI());
        }
        return EntryMatchers.isCreation(request);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(isAttempt(request) || isCreation(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        TokenBucket.Decision decision = isAttempt(request)
                ? limiter.checkAttempt(ip, visitorKey(request))
                : limiter.checkCreate(ip, visitorKey(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        resolver.resolveException(request, response, null, new ApiException(ErrorCode.RATE_LIMITED, decision.retryAfter()));
    }

    private static String visitorKey(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, VisitorAuthenticationFilter.COOKIE_NAME);
        if (cookie == null || !Tokens.isWellFormed(cookie.getValue())) {
            return null;
        }
        return Tokens.sha256Hex(cookie.getValue());
    }
}
