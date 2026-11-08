package com.example.quickvote.common;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * アプリ独自の設定値（application.yaml の quickvote.*）。
 * 人数上限などの「仕様」は設定ではなくコード（PollRules）に置き、運用で変えたい値だけをここに置く。
 * <p>
 * 不正な値は起動時に落とす（fail fast）。型が Duration や int というだけでは 0 や負の値も通ってしまい、
 * 例えば idempotency.retention=0s だと、再送されたリクエストの記録がすぐ期限切れ扱いで消え、二重登録が起きる。
 * そこで、時間は @DurationMin / @DurationMax で許容範囲を宣言する（範囲は各項目のコメントに書いた）。
 */
@ConfigurationProperties(prefix = "quickvote")
@Validated
public record QuickVoteProperties(
        @DefaultValue @Valid VisitorCookie visitorCookie,
        @DefaultValue @Valid RateLimit rateLimit,
        @DefaultValue @Valid Sse sse,
        @DefaultValue @Valid Cleanup cleanup,
        @DefaultValue @Valid Idempotency idempotency) {

    /** secure: 開発(HTTP)では false、本番(HTTPS)では true。 */
    public record VisitorCookie(@DefaultValue("false") boolean secure) {
    }

    /**
     * トークンバケット（容量と、1分あたりの補充数）。IP・訪問者の単位ごとに数える。
     * <ul>
     * <li>ip: 訪問者を発行する入口（投票の作成・参加・招待の受諾）と招待の発行の共通。Cookie を捨てて何度も来る相手を止める</li>
     * <li>visitor: 参加コード・招待トークンを試す回数（POST /api/join、招待の受諾）</li>
     * <li>create: 投票の作成・招待の発行の回数</li>
     * </ul>
     */
    public record RateLimit(
            @DefaultValue("100") @Positive int ipCapacity,
            @DefaultValue("60") @Positive double ipRefillPerMinute,
            @DefaultValue("10") @Positive int visitorCapacity,
            @DefaultValue("10") @Positive double visitorRefillPerMinute,
            @DefaultValue("10") @Positive int createCapacity,
            @DefaultValue("10") @Positive double createRefillPerMinute) {
    }

    /**
     * SSE。
     *
     * @param heartbeatInterval ハートビートの間隔（1秒〜5分）
     * @param timeout           1接続の最大時間（1分〜24時間）。過ぎるとサーバーが閉じ、ブラウザが再接続する
     * @param sendTimeout       1回の送信がこの時間を超えて終わらない購読者は、詰まっているとみなして切る（1秒〜5分）
     * @param deliveryThreads   イベントを購読者へ書き込むスレッドの数（1〜64）
     */
    public record Sse(
            @DefaultValue("15s") @DurationMin(seconds = 1) @DurationMax(minutes = 5) Duration heartbeatInterval,
            @DefaultValue("30m") @DurationMin(minutes = 1) @DurationMax(hours = 24) Duration timeout,
            @DefaultValue("10s") @DurationMin(seconds = 1) @DurationMax(minutes = 5) Duration sendTimeout,
            @DefaultValue("4") @Min(1) @Max(64) int deliveryThreads) {
    }

    /**
     * 期限切れデータの削除。
     *
     * @param interval 実行間隔（10秒〜1日）
     */
    public record Cleanup(
            @DefaultValue("5m") @DurationMin(seconds = 10) @DurationMax(days = 1) Duration interval,
            @DefaultValue("100") @Positive int batchSize,
            @DefaultValue("50") @Positive int maxBatchesPerRun) {
    }

    /**
     * 冪等性記録の保持期間（1分〜7日）。クライアントが再送しうる期間より短くしてはいけない。
     * 0 や負の値だと、記録がすぐ期限切れとして消され、再送が「新しい操作」として再実行されてしまう。
     */
    public record Idempotency(
            @DefaultValue("24h") @DurationMin(minutes = 1) @DurationMax(days = 7) Duration retention) {
    }
}
