package com.example.review2.bad;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookie QV_VISITOR から訪問者を特定する。Cookie がなければ新しい訪問者として発行する。 */
@Component
public class VisitorFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "QV_VISITOR";

    private final AtomicLong sequence = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long visitorId = readVisitorId(request);
        if (visitorId == null) {
            visitorId = sequence.incrementAndGet();
            Cookie cookie = new Cookie(COOKIE_NAME, String.valueOf(visitorId));
            cookie.setPath("/");
            cookie.setMaxAge(30 * 24 * 60 * 60);
            response.addCookie(cookie);
        }
        request.setAttribute("visitorId", visitorId);
        chain.doFilter(request, response);
    }

    private static Long readVisitorId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return Long.parseLong(cookie.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
