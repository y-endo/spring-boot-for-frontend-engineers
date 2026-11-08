package com.example.quickvote.journey;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 自作 Filter の最小形。リクエストが入ってくるとき（chain.doFilter の前）と、戻ってくるとき（後）に1行ずつ出す。
 * OncePerRequestFilter を継承すると、1リクエストで1回だけ動くことが保証される。
 */
@Component
@Profile("journey")
@Order(10) // 小さいほど外側（先に通る）。RequestIdFilter（HIGHEST_PRECEDENCE）の内側。
public class JourneyLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Journey.step("Filter 入る", request.getMethod() + " " + request.getRequestURI());
        try {
            chain.doFilter(request, response); // この先（次の Filter、DispatcherServlet ...）へ進む
        } finally {
            Journey.step("Filter 戻る", "status=" + response.getStatus());
        }
    }
}
