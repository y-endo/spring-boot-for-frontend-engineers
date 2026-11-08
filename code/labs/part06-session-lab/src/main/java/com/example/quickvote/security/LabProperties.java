package com.example.quickvote.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * このラボだけの「壊してみる」スイッチ（lab.*）。既定値はすべて、正しい（完成形と同じ）動作。
 * 本番のコードにこういうスイッチは置かない。章の実験で、わざと壊した状態を再現するためのもの。
 *
 * @param saveContext      認証フィルタが SecurityContext をリポジトリへ保存するか（第32章。false にすると SSE の完了で認証が失われる）
 * @param rateLimit        回数制限が URL を判定する方法。path-pattern（正）/ raw-uri（getRequestURI の文字列比較。第33章）/ none
 * @param csrf             CSRF 対策を有効にするか（第34章。false は攻撃の再現用）
 * @param corsAllowOrigin  CORS で許可するオリジン。空なら CORS 設定なし（第34章）
 * @param joinCodeLength   参加コードの長さ。8 が仕様。短くすると総当たりで破れる（第33章）
 * @param sessionManagement SessionManagementFilter を有効にするか（第32章。false にすると、暗黙の保存が働かなくなる）
 * @param bucketOnIpReject IP単位で拒否した後も、訪問者単位のバケットを作るか（第35章。true が元のバグ）
 */
@ConfigurationProperties(prefix = "lab")
public record LabProperties(
        @DefaultValue("true") boolean saveContext,
        @DefaultValue("path-pattern") String rateLimit,
        @DefaultValue("true") boolean csrf,
        @DefaultValue("") String corsAllowOrigin,
        @DefaultValue("8") int joinCodeLength,
        @DefaultValue("true") boolean sessionManagement,
        @DefaultValue("false") boolean bucketOnIpReject) {
}
