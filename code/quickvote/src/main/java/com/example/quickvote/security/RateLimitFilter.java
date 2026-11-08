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
 * 誰でも叩ける入口（投票の作成・参加コードでの参加・招待の受諾）と、投票・招待を作るリクエストの前段で
 * 試行回数を数え、超えたら 429 ＋ Retry-After を返す。
 * <p>
 * 訪問者を発行するフィルタ（VisitorAuthenticationFilter）より前に置く。
 * 後ろに置くと、Cookie なしで連打する攻撃者が、拒否される前に visitors の行を作り続けてしまう。
 * 訪問者の識別には DB を引かず、Cookie の値のハッシュをそのままキーにする（偽の Cookie を作っても、
 * IP単位の制限がかかるので突破できない）。
 * 入口の判定は EntryMatchers と共通（エンコードされた URL でもすり抜けられない）。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final HandlerExceptionResolver resolver;

    public RateLimitFilter(RateLimiter limiter, HandlerExceptionResolver resolver) {
        this.limiter = limiter;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(EntryMatchers.isAttempt(request) || EntryMatchers.isCreation(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getRemoteAddr は直接つながっている相手のIP。リバースプロキシの後ろでは、プロキシのIPになってしまう。
        // 本番で X-Forwarded-For を使うなら server.forward-headers-strategy を設定し、信頼できるプロキシだけに限る。
        String ip = request.getRemoteAddr();
        TokenBucket.Decision decision = EntryMatchers.isAttempt(request)
                ? limiter.checkAttempt(ip, visitorKey(request))
                : limiter.checkCreate(ip, visitorKey(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        // フィルタの中で起きた例外も @RestControllerAdvice に処理させ、他のエラーと同じ ProblemDetail にする。
        resolver.resolveException(request, response, null, new ApiException(ErrorCode.RATE_LIMITED, decision.retryAfter()));
    }

    /** Cookie の訪問者トークンのハッシュ。Cookie がない、または形式が違う値は null（形式違いの値でバケットを作らせない）。 */
    private static String visitorKey(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, VisitorAuthenticationFilter.COOKIE_NAME);
        if (cookie == null || !Tokens.isWellFormed(cookie.getValue())) {
            return null;
        }
        return Tokens.sha256Hex(cookie.getValue());
    }
}
