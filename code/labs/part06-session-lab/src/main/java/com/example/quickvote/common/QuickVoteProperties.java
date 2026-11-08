package com.example.quickvote.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * アプリの設定値（application.yaml の quickvote.*）。完成形（code/quickvote）と同じ名前・既定値。
 * 範囲外の値は起動時に落とす（fail fast）。
 */
@ConfigurationProperties(prefix = "quickvote")
@Validated
public record QuickVoteProperties(
        @DefaultValue @Valid VisitorCookie visitorCookie,
        @DefaultValue @Valid RateLimit rateLimit) {

    /** 訪問者 Cookie と CSRF Cookie の Secure 属性。開発（HTTP）は false、本番（HTTPS）は true。 */
    public record VisitorCookie(@DefaultValue("false") boolean secure) {
    }

    /**
     * 回数制限（トークンバケット）。ip: 接続元IP単位（全入口共通）、visitor: 参加コード・招待を試す回数、create: 投票の作成回数。
     */
    public record RateLimit(
            @DefaultValue("100") @Positive int ipCapacity,
            @DefaultValue("60") @Positive double ipRefillPerMinute,
            @DefaultValue("10") @Positive int visitorCapacity,
            @DefaultValue("10") @Positive double visitorRefillPerMinute,
            @DefaultValue("10") @Positive int createCapacity,
            @DefaultValue("10") @Positive double createRefillPerMinute) {
    }
}
