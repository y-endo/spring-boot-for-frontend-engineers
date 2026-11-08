package com.example.quickvote.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * 認証済みの訪問者を表す Authentication。権限（GrantedAuthority）は持たせない。
 * QuickVote の認可は「投票ごとの参加者表」で決まり、アプリ全体のロールでは決まらないため。
 * Spring Security の「匿名認証（AnonymousAuthenticationToken）」とは別物:
 * こちらは「Cookie で特定できた訪問者」、あちらは「何も特定できなかった」状態。
 */
public class VisitorAuthentication extends AbstractAuthenticationToken {

    private final VisitorPrincipal principal;

    public VisitorAuthentication(VisitorPrincipal principal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public VisitorPrincipal getPrincipal() {
        return principal;
    }
}
