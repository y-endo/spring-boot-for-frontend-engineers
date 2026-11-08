package com.example.review2.fixed;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cookie QV_VISITOR のトークンから訪問者を特定する。
 * 新しい訪問者を発行するのは「入口」の2つだけ。知らないトークンは「Cookie なし」と同じに扱う。
 */
@Component
public class VisitorFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "QV_VISITOR";

    private static final RequestMatcher ENTRY = new org.springframework.security.web.util.matcher.OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/polls"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/invites/{token}/accept"));

    private final VisitorSessions sessions;

    public VisitorFilter(VisitorSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> visitorId = readToken(request).flatMap(sessions::find);
        if (visitorId.isEmpty() && ENTRY.matches(request)) {
            String token = sessions.issue();
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(false)               // 本番は true（HTTPS 終端の後ろ）。演習のテストは HTTP
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(30))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            visitorId = sessions.find(token);
        }
        visitorId.ifPresent(id -> request.setAttribute("visitorId", id));
        chain.doFilter(request, response);
    }

    private static Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
