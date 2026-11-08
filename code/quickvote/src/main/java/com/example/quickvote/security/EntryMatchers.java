package com.example.quickvote.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 「誰でも叩ける入口」と、それに準じて回数を制限したいリクエストの判定。
 * 訪問者を発行するフィルタ（VisitorAuthenticationFilter）と、回数制限のフィルタ（RateLimitFilter）が同じ判定を使う。
 * <p>
 * URL の判定に {@code request.getRequestURI()} と文字列の比較を使ってはいけない。getRequestURI() は
 * ブラウザが送った生の文字列（"/api/%6aoin" のようなパーセントエンコード付き）を返すのに対し、
 * Spring MVC は各セグメントをデコードしてからハンドラーを探すので、"/api/%6aoin" は "/api/join" として処理される。
 * 文字列の比較だと、エンコードした URL で制限をすり抜けられる。
 * PathPatternRequestMatcher は MVC と同じ PathPattern で、デコード後のパスを、コンテキストパスを除いて判定する。
 */
final class EntryMatchers {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** 投票を作る。訪問者 Cookie をまだ持たない人でも使える入口。 */
    static final RequestMatcher CREATE_POLL = PATHS.matcher(HttpMethod.POST, "/api/polls");
    /** 参加コードで参加する。同上。 */
    static final RequestMatcher JOIN = PATHS.matcher(HttpMethod.POST, "/api/join");
    /** 招待を受ける。同上。 */
    static final RequestMatcher ACCEPT_INVITE = PATHS.matcher(HttpMethod.POST, "/api/invites/{token}/accept");
    /** 共同主催者の招待を発行する（主催者のみ。訪問者の新規発行はしない）。 */
    static final RequestMatcher ISSUE_INVITE = PATHS.matcher(HttpMethod.POST, "/api/polls/{pollId}/invites");

    private EntryMatchers() {
    }

    /** 訪問者を新しく発行してよいリクエストか。 */
    static boolean issuesVisitor(HttpServletRequest request) {
        return CREATE_POLL.matches(request) || JOIN.matches(request) || ACCEPT_INVITE.matches(request);
    }

    /** 参加コードや招待トークンを「試す」リクエストか（総当たりの対象）。 */
    static boolean isAttempt(HttpServletRequest request) {
        return JOIN.matches(request) || ACCEPT_INVITE.matches(request);
    }

    /** 投票や招待を「作る」リクエストか。 */
    static boolean isCreation(HttpServletRequest request) {
        return CREATE_POLL.matches(request) || ISSUE_INVITE.matches(request);
    }

}
