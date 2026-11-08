package com.example.quickvote.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.common.Tokens;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Cookie の訪問者トークンから利用主体を特定し、SecurityContext に VisitorAuthentication を載せるカスタム認証フィルタ。
 * 完成形（code/quickvote）との差は、visitors 表がメモリであること（touch による有効期限の延長もなし）と、
 * 実験用スイッチ lab.save-context だけ。
 * <p>
 * 訪問者を新しく発行するのは「誰でも使える入口」だけ（投票の作成・参加コードでの参加・招待の受諾）。
 */
public class VisitorAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "QV_VISITOR";
    static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final VisitorRepository visitors;
    private final boolean secureCookie;
    private final SecurityContextRepository securityContextRepository;
    private final boolean saveContext;

    public VisitorAuthenticationFilter(VisitorRepository visitors, QuickVoteProperties properties,
            SecurityContextRepository securityContextRepository, boolean saveContext) {
        this.visitors = visitors;
        this.secureCookie = properties.visitorCookie().secure();
        this.securityContextRepository = securityContextRepository;
        this.saveContext = saveContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        // 形式が違う値（長すぎる・出まかせ）は、探しに行かずに捨てる。
        String token = cookie == null || !Tokens.isWellFormed(cookie.getValue()) ? null : cookie.getValue();

        Optional<Long> visitorId = token == null
                ? Optional.empty()
                : visitors.findIdByTokenHash(Tokens.sha256Hex(token));

        if (visitorId.isEmpty() && EntryMatchers.issuesVisitor(request)) {
            String newToken = Tokens.newToken();
            visitorId = Optional.of(visitors.create(Tokens.sha256Hex(newToken)));
            addCookie(response, newToken);
        }

        visitorId.ifPresent(id -> {
            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(new VisitorAuthentication(new VisitorPrincipal(id)));
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
            if (saveContext) {
                securityContextRepository.saveContext(context, request, response);
            }
        });
        chain.doFilter(request, response);
    }

    private void addCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
