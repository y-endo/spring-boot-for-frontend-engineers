package com.example.tsjava;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Express:  app.use((req, res, next) => { res.set("X-Filter", "on"); next() })
@Component
class TimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Filter", "on");             // 応答を書き始める前に足す
        long t0 = System.nanoTime();
        try {
            chain.doFilter(request, response);            // next()
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;   // next() が返ってきた後の処理（応答は書き終わっている）
            logger.info("elapsed " + ms + "ms");
        }
    }
}
