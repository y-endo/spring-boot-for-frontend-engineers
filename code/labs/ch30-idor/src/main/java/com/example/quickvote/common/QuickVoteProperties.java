package com.example.quickvote.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * アプリ独自の設定値（application.yaml の quickvote.*）。
 * 人数上限などの「仕様」は設定ではなくコード（PollRules）に置き、運用で変えたい値だけをここに置く。
 * 不正な値は起動時に落とす（fail fast）。
 * <p>
 * 完成形の QuickVoteProperties（visitorCookie・rateLimit・sse・cleanup・idempotency）は、それぞれの部で加わる。
 * ここにある standInAuth は第6部（本物の匿名セッション）で消える暫定の設定。
 */
@ConfigurationProperties(prefix = "quickvote")
@Validated
public record QuickVoteProperties(
        @DefaultValue @Valid StandInAuth standInAuth,
        @DefaultValue @Valid Demo demo) {

    /**
     * 暫定の「訪問者の名乗り」。このヘッダーの値を訪問者IDとして信用する（誰でも偽装できる。第6部で置き換える）。
     * @Pattern は HTTP ヘッダー名として使える文字だけを許す。環境変数の置き忘れ（"${NAME}" が展開されずに残る）も、これで起動時に落とせる。
     */
    public record StandInAuth(
            @DefaultValue("X-Visitor-Id") @NotBlank @Pattern(regexp = "[A-Za-z0-9-]+") String header) {
    }

    /** 開発用の見本データ。seed=true なら起動時に見本の投票を1つ作る（dev プロファイルで有効にする）。 */
    public record Demo(
            @DefaultValue("false") boolean seed,
            @DefaultValue("3") @Min(0) @Max(20) int members) {
    }
}
