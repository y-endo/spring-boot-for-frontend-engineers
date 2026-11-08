package com.example.quickvote.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.example.quickvote.common.Db;
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
 * HttpSession は使わない（STATELESS）。リクエストのたびに Cookie → ハッシュ → visitors を引く。
 * セッションをサーバーのメモリに持たないので、再起動しても、複数台でも、同じ訪問者として扱える。
 * <p>
 * 訪問者を新しく発行するのは「誰でも使える入口」だけ（投票の作成・参加コードでの参加・招待の受諾）。
 * それ以外のAPIで Cookie がなければ、認証なし（Anonymous）のままにして 401 になる。
 * 何でも発行すると、bot が叩くだけで visitors の行が無限に増えるため。
 */
public class VisitorAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "QV_VISITOR";
    static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);
    /** last_seen_at の更新（と Cookie の延長）を行う最短間隔。 */
    static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final VisitorRepository visitors;
    private final Clock clock;
    private final boolean secureCookie;
    private final SecurityContextRepository securityContextRepository;

    public VisitorAuthenticationFilter(VisitorRepository visitors, Clock clock, QuickVoteProperties properties,
            SecurityContextRepository securityContextRepository) {
        this.visitors = visitors;
        this.clock = clock;
        this.secureCookie = properties.visitorCookie().secure();
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = Db.now(clock);
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        // 形式が違う値（長すぎる・出まかせ）は DB を引かずに捨てる。
        String token = cookie == null || !Tokens.isWellFormed(cookie.getValue()) ? null : cookie.getValue();

        Optional<Long> visitorId = token == null
                ? Optional.empty()
                : visitors.findIdByTokenHash(Tokens.sha256Hex(token));

        if (visitorId.isPresent()) {
            // 使われ続ける限り Cookie の有効期限も延ばす（最後のアクセスから30日）。
            if (visitors.touch(visitorId.get(), now, now.minus(TOUCH_INTERVAL))) {
                addCookie(response, token);
            }
        } else if (EntryMatchers.issuesVisitor(request)) {
            String newToken = Tokens.newToken();
            visitorId = Optional.of(visitors.create(Tokens.sha256Hex(newToken), now));
            addCookie(response, newToken);
        }

        visitorId.ifPresent(id -> {
            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(new VisitorAuthentication(new VisitorPrincipal(id)));
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
            // Spring Security 6 以降、認証フィルタは SecurityContext をリポジトリへ「明示的に保存」する必要がある
            // （ホルダーに置くだけでは、そのスレッドのその場限り）。ここでは STATELESS 用の
            // RequestAttributeSecurityContextRepository で、リクエストの属性に保存する（セッションは使わない）。
            // 保存しないと、SSE のように非同期になるリクエストが終わるときの ASYNC ディスパッチ
            // （この OncePerRequestFilter はスキップされる）で、認可がもう一度走ったときに認証が失われ、
            // AuthorizationDeniedException が出る（応答はすでにコミット済みなので、エラーを返すこともできない）。
            securityContextRepository.saveContext(context, request, response);
        });
        chain.doFilter(request, response);
    }

    /**
     * Cookie 属性: HttpOnly（JSから読めない＝XSSで盗まれにくい）、SameSite=Lax（他サイトからの
     * POST では送られない）、Path=/、Max-Age 30日。Secure は設定で切り替える（開発 false / 本番 true）。
     */
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
