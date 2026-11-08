package com.example.quickvote.security;

/**
 * 「誰として扱うか」。Authentication の principal に載る、匿名の訪問者を表す値。
 * ログインしているわけではなく、Cookie の訪問者トークンから特定した利用主体（visitors.id）。
 * コントローラーでは @AuthenticationPrincipal で受け取る。
 */
public record VisitorPrincipal(long visitorId) {
}
