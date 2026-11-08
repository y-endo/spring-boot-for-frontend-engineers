# 第31章のラボ：Spring Security の地図

Initializr（`web`、`security`）で作った最小のアプリです。プロファイルで、段階を切り替えます（ポート 18031）。

| 起動 | 段階 | 観察すること |
| --- | --- | --- |
| `./mvnw spring-boot:run` | 依存を足しただけ | 全URLが 401、`WWW-Authenticate: Basic`、`JSESSIONID`、起動ログの使い捨てパスワード |
| `-Dspring-boot.run.profiles=open` | 全部許可 | 何も守られていない。フィルタの列が縮む |
| `-Dspring-boot.run.profiles=closed` | 許可リスト方式 | `/hello` 200、`/api/**` 401、それ以外 401、`POST` は CSRF で 403 |
| `-Dspring-boot.run.profiles=twice` | 自作フィルタを `@Component` にした | `Filter` 直接実装は2回、`OncePerRequestFilter` は1回 |
| `-Dspring-boot.run.profiles=badorder` | `anyRequest()` の後ろに規則 | 起動時に `Can't configure requestMatchers after anyRequest` |

Windows は `.\mvnw.cmd`。フィルタの一覧は `logging.level.org.springframework.security.web.DefaultSecurityFilterChain=DEBUG` で、起動ログに出ます。
