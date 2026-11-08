# QuickVote 実装ノート（教材執筆者向け）

このノートは、教材の本文を書く人が「このコードを正として」書くための資料です。
**ここに書いた import・アノテーション・設定キー・スターター名は、すべてこのプロジェクトで実際にビルドを通し、テストで動作を確認したものです。**
記憶や古い記事から API を書かないでください。迷ったら、このプロジェクトのコードを grep してください。

- 対象: Spring Boot 4.1.2 / Spring Framework 7.0.10 / Spring Security 7.1.2 / Jackson 3.1.7 / JUnit 6.0.3 / Testcontainers 2.0.5 / PostgreSQL 18 / Java 25
- 仕様の正: `production/CURRICULUM.md` の「3. 通しの題材」。このコードは、その仕様表どおりに作ってあります。
- 本文のコードは、このコードと矛盾してはいけません（CURRICULUM 8 章）。途中段階のコードを載せるときは、最終形との差分に注意してください。
- **シニアレビューの指摘（18件）を反映済みです。** 変更点は 10 章にまとめました。API が変わった点（投票作成の締切は `closesInMinutes`、締切後の招待受諾は 409、`INVALID_CLOSES_AT` の廃止）に注意してください。

目次

1. 全体像
2. パッケージとクラスの一覧
3. Flyway マイグレーションの一覧（章との対応）
4. 実際に確認した import・アノテーション・設定キー・スターター名
5. 古い世代との違いで、実際にハマった点
6. 主要な設計判断と理由
7. 各テストが証明していること
8. 実行したコマンドと結果
9. 既知の限界・妥協点・未実装
10. シニアレビューへの対応（変更点）
11. 追加修正の履歴（変更履歴）

---

## 1. 全体像

```
ブラウザ(静的クライアント) ──HTTP/JSON──▶ [Tomcat]
                                           │
        RequestIdFilter（相関ID・アクセスログ1行）
                                           │
        Spring Security フィルタチェーン
          CsrfFilter → RateLimitFilter → VisitorAuthenticationFilter → AuthorizationFilter
                                           │
        DispatcherServlet → Controller → Service(@Transactional) → Repository(JdbcClient) ──▶ PostgreSQL
                                                    │
                                   poll_events に保存 ＋ pg_notify（同じトランザクション）
                                                    │ COMMIT で NOTIFY が配送される
        PollEventListener(LISTEN専用接続) ◀─────────┘（他インスタンスにも届く）
                │ onNotify(pollId)
        PollEventHub（メモリ内の購読者名簿）→ 配信プール（専用スレッド）が、DBから「最後に送ったseqより後」を読み直して SseEmitter に送る
```

- 時刻は `Clock` Bean 経由（`Db.now(clock)`）。DB の `now()` は使いません。テストで時間を進められます。
- 現在時刻は **マイクロ秒に丸めて** 使います（`timestamptz` の精度に合わせ、往復で値が変わらないように）。
- 権限判定は「投票ごとの参加者表」だけが根拠です。アプリ全体のロール（GrantedAuthority）は持ちません。
- 認可の入口は `ParticipantService.require / requireHost` の1か所です。各サービスは最初にこれを呼びます。

---

## 2. パッケージとクラスの一覧

ルートは `com.example.quickvote`。依存の向き: `poll` → `participant`, `event`, `common`。`vote`・`invite` → `poll`, `participant`, `event`。`security` は他に依存しません（`common` のみ）。

### `common` — 横断的な部品

| クラス | 役割 |
| --- | --- |
| `QuickVoteProperties` | `quickvote.*` の設定。record ＋ `@ConfigurationProperties` ＋ `@Validated` ＋ `@DefaultValue`。時間は `@DurationMin` / `@DurationMax`、数は `@Positive` / `@Min` / `@Max` で範囲を宣言し、**範囲外（0・負の値を含む）は起動時に落とす**（`QuickVotePropertiesTest`） |
| `ClockConfig` | `Clock.systemUTC()` の Bean。テストでは `@Primary` の `MutableClock` が優先される |
| `ErrorCode` | 業務エラーの一覧（enum）。HTTP ステータスと既定メッセージを持つ。`code` として応答に出る |
| `ApiException` | 想定内の業務エラー。`ErrorCode` を持つ。429 のときは `retryAfter` も持つ |
| `ProblemDetailAdvice` | `@RestControllerAdvice`（`ResponseEntityExceptionHandler` を継承）。すべての例外を ProblemDetail に変換し `code` を付ける。`instance` には相関ID（`urn:request-id:...`）を入れる。想定外の例外は `SafeThrowable.of(ex)` でログに出す |
| `SafeThrowable` | ログに渡す例外を「型・SQLSTATE・スタックのフレームだけ」に作り替える（メッセージと原因のメッセージは捨てる）。DB の制約違反のメッセージには違反した行の中身が入るため |
| `RequestIdFilter` | `X-Request-Id` を受ける/採番 → MDC `requestId` → 応答ヘッダー。リクエスト終了時にログを1行（メソッド・ルートのパターン・ステータス・所要時間のみ） |
| `BoundedText` / `BoundedTextValidator` | 自作の Bean Validation 制約。**コードポイント数**（この仕様での「文字数」）と UTF-8 バイト数の上限 |
| `Tokens` | 32バイト乱数→Base64URL のトークン生成、SHA-256（16進）、`isWellFormed`（Cookie の値が43文字の Base64URL か。DB を引く・バケットを作る前の形式チェック）。訪問者・招待トークンで共用 |
| `Db` | `Instant` ↔ `OffsetDateTime` の変換、`Clock` からマイクロ秒に丸めた現在時刻 |
| `IdempotencyService` | `Idempotency-Key` による再送の吸収（方式は 6.3）。記録が途中で消えたら先取りからやり直す |
| `IdempotencyRepository` | `idempotency_records` の SQL。再送の記録は `findForShare`（`FOR SHARE`）で読む |

### `poll` — 投票（集約の中心）

| クラス | 役割 |
| --- | --- |
| `PollRules` | 仕様上の定数（参加者50・選択肢10・文字数（コードポイント数）・締切の範囲（5〜10080分）・保持7日） |
| `Poll` / `PollStatus` | polls の1行（record）と状態（OPEN / CLOSED）。`isClosedAt(now)` が締切判定（`closes_at` ちょうどは締切後） |
| `PollRepository` | polls の SQL。`lockById` / `lockByJoinCode` が `FOR UPDATE`（親行ロック） |
| `OptionRepository` | options の SQL。`findWithVoteCounts` は LEFT JOIN で票0の選択肢も返す |
| `PollService` | 作成（締切は `Clock` の現在時刻 + `closesInMinutes` で計算。参加コード衝突のリトライ）、詳細（REPEATABLE READ）、選択肢追加（冪等）、締切、削除 |
| `PollController` | `/api/polls` 系。`Idempotency-Key` の形式検証もここ |
| `CreatePollRequest` / `AddOptionRequest` | リクエストの record。コンパクトコンストラクタで strip。作成の締切は `Integer closesInMinutes`（`@Min(5)` `@Max(10080)`） |
| `PollDetail` / `OptionResponse` | 応答の record |
| `JoinService` / `JoinController` / `JoinRequest` / `JoinResponse` | `POST /api/join`。人数上限を親行ロックで守る |
| `JoinCodeGenerator` | 参加コード（31種×8文字）の生成・正規化・形式判定 |
| `VoteShareCalculator` | 得票率（BigDecimal＋最大剰余法、小数1桁、合計100.0） |
| `ExpiredDataCleaner` | `@Scheduled` の期限切れ削除。`pg_try_advisory_lock` で1台だけ実行 |

### `participant` — 参加者と認可の入口

| クラス | 役割 |
| --- | --- |
| `ParticipantRole` | HOST / MEMBER |
| `ParticipantContext` | 「この訪問者はこの投票のこの参加者」という認可済みの事実（record） |
| `ParticipantRepository` | participants の SQL。`findActive` は投票の存在・参加の有無を区別しない（どちらも空） |
| `ParticipantService` | `require`（非参加者→404）/ `requireHost`（MEMBER→403） |

### `vote` / `invite`

| クラス | 役割 |
| --- | --- |
| `VoteService` / `VoteController` / `VoteRepository` | `PUT /api/polls/{id}/my-vote`。`upsert` は UNIQUE ＋ `ON CONFLICT DO UPDATE ... WHERE option_id <> EXCLUDED.option_id` |
| `VoteRequest` / `VoteResponse` | record |
| `InviteService` / `InviteController` / `InviteRepository` | 招待の発行と受諾。`consume` が `UPDATE ... WHERE used_at IS NULL AND expires_at > ?`。**締切後の受諾は 409 `POLL_CLOSED`**（親行ロック後に判定し、消費はロールバックされる） |
| `InviteResponse` | 発行結果（トークンはこの応答でしか分からない） |

### `event` — SSE とイベント履歴

| クラス | 役割 |
| --- | --- |
| `PollEvent` | poll_events の1行（record）。`seq` が SSE の `id:` |
| `PollEventRepository` | 採番（`UPDATE polls SET last_event_seq = last_event_seq + 1 ... RETURNING`）、INSERT、`pg_notify`、履歴取得 |
| `PollEventService` | `append`（`Propagation.MANDATORY`。採番→保存→NOTIFY）、履歴・最新seqの取得（`PAGE_SIZE`＝500件ずつ） |
| `PollEventHub` | 購読者名簿（`ConcurrentHashMap`、登録・解除は `compute` 系）と配信。書き込みは**専用の配信プール**で、購読者ごとに同時に1つ・最新まで追いつくまでページを読み進める。`subscribe`（登録→再送を頼む）、`onNotify`・`deliverAll`（頼むだけで即戻る）、`sendHeartbeats`（`@Scheduled`。ハートビートを頼み、書き込みが `sendTimeout` を超えて詰まった購読者を打ち切る）、`closeAll`（待ち上限 3 秒） |
| `PollEventListener` | `SmartLifecycle`。専用スレッド＋専用接続で `LISTEN poll_events`。切断時は再接続して `deliverAll` |
| `PollEventController` | `GET /api/polls/{id}/events`。接続時に認可（参加者のみ）、`Last-Event-ID` を解釈 |

### `security` — 匿名セッション・Security 設定・レート制限

| クラス | 役割 |
| --- | --- |
| `SecurityConfig` | `SecurityFilterChain`（ラムダDSL）。STATELESS、`csrf.spa()`、`RequestAttributeSecurityContextRepository`、CSP、許可リスト方式の認可 |
| `EntryMatchers` | 「誰でも叩ける入口」（`POST /api/polls`・`/api/join`・`/api/invites/{token}/accept`）と招待発行の判定。`PathPatternRequestMatcher`（MVC と同じデコード後のパス・コンテキストパス除外）。訪問者の発行と回数制限の両フィルタが共有 |
| `VisitorAuthenticationFilter` | Cookie `QV_VISITOR` → ハッシュ → `visitors` → `VisitorAuthentication` を SecurityContext へ**保存**（`SecurityContextRepository.saveContext`）。入口3つだけ新規発行 |
| `VisitorPrincipal` / `VisitorAuthentication` | principal（`visitorId`）と Authentication（権限なし） |
| `VisitorRepository` | visitors の SQL（`touch` は1時間に1回だけ更新） |
| `RateLimitFilter` | 入口（参加・招待の受諾・投票の作成）と招待発行の前段で回数を数え、超過で 429（訪問者発行より前に置く） |
| `RateLimiter` | IP単位（全入口共通）と訪問者単位（参加・受諾の「試行」用、作成・発行の「作成」用）の `TokenBucketLimiter`。IP単位で拒否したら訪問者単位のバケットは作らない。`@Scheduled` で掃除 |
| `TokenBucketLimiter` / `TokenBucket` | 自作トークンバケット。`ConcurrentHashMap#compute` の中でだけバケットを触る（消費も掃除も、キー単位で直列）。`TokenBucket` 自体はスレッドセーフではない |
| `ProblemDetailSecurityHandler` | 401（EntryPoint）と 403（AccessDeniedHandler）を `HandlerExceptionResolver` 経由で ProblemDetail にする |

### リソース

| ファイル | 役割 |
| --- | --- |
| `application.yaml` / `application-prod.yaml` | 設定。prod は Cookie の Secure を有効にし、compose 連携を切り、**Actuator を管理ポート 9090 に移す** |
| `db/migration/V1〜V6` | Flyway |
| `static/index.html` `app.js` `app.css` | 静的クライアント（fetch＋EventSource。`innerHTML` は不使用） |
| `compose.yaml` | 開発用 PostgreSQL 18（ホスト側 15432） |
| `compose.app.yaml` | アプリ＋DB（本番に近い構成の確認用） |
| `Dockerfile` | マルチステージ・layered jar・非root |

---

## 3. Flyway マイグレーションの一覧（章との対応）

| ファイル | 内容 | 導入を想定する章 |
| --- | --- | --- |
| `V1__create_polls_and_options.sql` | `polls`（CHECK 制約・UNIQUE・`delete_after` の索引）、`options`（ラベルの CHECK・`UNIQUE(poll_id, id)`） | 第17章（V1の作り方）。制約の読み解きは第18章 |
| `V2__create_visitors_participants_votes.sql` | `visitors`、`participants`（`UNIQUE(poll_id, visitor_id)`）、`votes`（`UNIQUE(poll_id, participant_id)`・複合外部キー）、`options.created_by` の外部キー | 第18章（participants・votes）。`visitors` と `participants.visitor_id` は CURRICULUM では第32章 |
| `V3__create_idempotency_records.sql` | `idempotency_records`（PK `(visitor_id, idem_key)`） | 第27章 |
| `V4__create_invites.sql` | `invites`（`token_hash` UNIQUE・`used_at`/`used_by`） | 第33章 |
| `V5__create_poll_events.sql` | `polls.last_event_seq` の追加、`poll_events`（PK `(poll_id, seq)`） | 第37章 |
| `V6__options_created_by_composite_fk.sql` | `options.created_by` の外部キーを単独列から複合 `(poll_id, created_by)` → `participants (poll_id, id)`（`ON DELETE CASCADE`）に置き換え。V2 のバグ修正 | 第18章（V2 の `options.created_by` の外部キーが単独列だったバグの修正。「適用済みの版は書き換えず、新しい版で直す」例にもなる） |

注意点（本文に影響します）:

- **V2 は CURRICULUM より前倒しで `visitors` を含みます。** `participants` が `visitors` を参照するため、外部キーの依存順でまとめました。
  本文で第18章と第32章に分けて見せたい場合は、第18章用の簡略版（`participants` に `visitor_id` なし）を本文用に別途書き、第32章で `ALTER TABLE` する流れになります。
  ただし既存行があると `NOT NULL` 列は追加できない点（expand/contract、第46章）に触れる必要があります。
- V番号は章の登場順（17→18→27→33→37）に合わせてあり、DBの外部キー依存とも矛盾しません。
- `options.created_by` の外部キーは V1 では張れない（`participants` がまだ無い）ので V2 で `ALTER TABLE` しています。**V2 では単独列 `(created_by) → participants (id)` で張ってしまっており、これがバグでした**（別の投票の参加者を作成者にした選択肢が入り、投票1の削除で投票2の選択肢まで連鎖して消える）。適用済みの V2 は書き換えず、**V6 で複合外部キー `(poll_id, created_by) → participants (poll_id, id)` に置き換えました**（参照先の `UNIQUE (poll_id, id)` は V2 の `participants_poll_id_id_key` が既にあり、`votes` の複合外部キーと共用）。既存データに `options.poll_id` と作成者の `participants.poll_id` が食い違う行があると V6 は失敗します（Flyway はトランザクションで流すので、失敗してもスキーマは V5 の状態に戻る）。洗い出しの SQL は V6 のコメントにあります。
- 主キーは `bigint GENERATED ALWAYS AS IDENTITY`、API に出す ID は `polls.public_id`（`uuid`、アプリ側で `UUID.randomUUID()`）。
- 文字数の CHECK は `char_length`（文字数）と `octet_length`（バイト数）を使い分けています。
- `participants.revoked_at` 列は仕様表にあるため作ってありますが、設定する API はありません（クエリは `revoked_at IS NULL` を条件にしています）。

---

## 4. 実際に確認した import・アノテーション・設定キー・スターター名

### 4.1 Maven の依存（`pom.xml`。バージョンは Boot の管理に任せ、個別指定なし）

Spring Initializr（`bootVersion=4.1.2`, `javaVersion=25`）から生成しました。Boot 4 では **機能ごとにスターターが分かれ、テスト用スターターも機能ごとにあります。**

| 用途 | artifactId |
| --- | --- |
| Web（Spring MVC） | `spring-boot-starter-webmvc`（`spring-boot-starter-web` ではない） |
| JDBC | `spring-boot-starter-jdbc` |
| Flyway | `spring-boot-starter-flyway` ＋ `org.flywaydb:flyway-database-postgresql`（Flyway 12.4.0） |
| Bean Validation | `spring-boot-starter-validation`（Hibernate Validator 9.1.5、Jakarta Validation 3.1.1） |
| Security | `spring-boot-starter-security` |
| Actuator | `spring-boot-starter-actuator`（Micrometer 1.17.2） |
| PostgreSQL ドライバ | `org.postgresql:postgresql`（42.7.14）。Initializr は `runtime` だが、`PGConnection` を直接使うため **compile スコープに変更** |
| Docker Compose 連携 | `spring-boot-docker-compose`（`runtime` / `optional`） |
| テスト | `spring-boot-starter-webmvc-test`、`-jdbc-test`、`-flyway-test`、`-security-test`、`-validation-test`、`-actuator-test` |
| Testcontainers | `spring-boot-testcontainers`、`org.testcontainers:testcontainers-junit-jupiter`、`org.testcontainers:testcontainers-postgresql`（2.0.5。**モジュール名が `postgresql` ではなく `testcontainers-postgresql`**） |
| 統合テスト実行 | `maven-failsafe-plugin`（バージョンは Boot の親 POM が管理）。`*IT` を `verify` で実行 |

その他: Tomcat 11.0.27、HikariCP 7.0.2、Mockito 5.23.0、AssertJ 3.27.7、Awaitility 4.3.0、Maven 3.10.0（Wrapper は `distributionType=only-script`＝wrapper jar なし）。
`jackson-annotations` は **2.21**（`com.fasterxml.jackson.annotation.*` のまま）、Jackson 本体は **`tools.jackson`** の 3.1.7。

### 4.2 import（このプロジェクトで実際に使い、コンパイルを通したもの）

Spring Boot 4:

```java
org.springframework.boot.autoconfigure.SpringBootApplication
org.springframework.boot.context.properties.ConfigurationProperties
org.springframework.boot.context.properties.ConfigurationPropertiesScan
org.springframework.boot.context.properties.bind.DefaultValue
org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails            // 旧: org.springframework.boot.autoconfigure.jdbc
org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration  // exclude 用
```

Spring Boot 4 のテスト:

```java
org.springframework.boot.test.context.SpringBootTest
org.springframework.boot.test.context.TestConfiguration
org.springframework.boot.test.web.server.LocalServerPort
org.springframework.boot.test.system.OutputCaptureExtension
org.springframework.boot.test.system.CapturedOutput
org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest            // 旧: org.springframework.boot.test.autoconfigure.web.servlet
org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint
org.springframework.boot.testcontainers.service.connection.ServiceConnection
org.testcontainers.postgresql.PostgreSQLContainer                         // 非ジェネリック。旧: org.testcontainers.containers.PostgreSQLContainer<?>
org.testcontainers.utility.DockerImageName
org.springframework.test.context.bean.override.mockito.MockitoBean       // Spring Framework のもの
org.springframework.test.context.bean.override.mockito.MockitoSpyBean
org.springframework.test.web.servlet.assertj.MockMvcTester
org.springframework.test.web.servlet.assertj.MvcTestResult
org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf / authentication
```

`@MockBean` / `@SpyBean`（`org.springframework.boot.test.mock.mockito`）は Boot 4.1.2 の `spring-boot-test` に **クラス自体が存在しません**（確認済み）。
`TestRestTemplate` は `org.springframework.boot.resttestclient.TestRestTemplate`（`spring-boot-resttestclient` モジュール）に移動しています（このプロジェクトでは JDK の `HttpClient` を使い、未使用）。

Spring Framework 7 / 周辺:

```java
org.springframework.jdbc.core.simple.JdbcClient
org.springframework.http.ProblemDetail
org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT                // UNPROCESSABLE_ENTITY は非推奨
org.springframework.http.ResponseCookie
org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
org.springframework.web.servlet.mvc.method.annotation.SseEmitter
org.springframework.web.method.annotation.HandlerMethodValidationException
org.springframework.web.servlet.HandlerExceptionResolver                 // Bean 名は "handlerExceptionResolver"
org.springframework.web.servlet.HandlerMapping                           // BEST_MATCHING_PATTERN_ATTRIBUTE
org.springframework.transaction.support.TransactionTemplate
org.springframework.transaction.annotation.Transactional / Propagation / Isolation
org.springframework.dao.DuplicateKeyException
org.jspecify.annotations.Nullable                                         // Spring 7 の null 安全は JSpecify
jakarta.validation.* / jakarta.servlet.* / jakarta.servlet.http.*         // javax.* は使わない
```

Jackson 3:

```java
tools.jackson.databind.json.JsonMapper   // 自動構成された Bean をコンストラクタ注入。writeValueAsString / readValue
```

`JsonMapper` の例外（`tools.jackson.core.JacksonException`）は **非検査例外**です。Jackson 2 のように try/catch や `throws` を書く必要はありません。

Spring Security 7:

```java
org.springframework.security.config.annotation.web.builders.HttpSecurity
org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
org.springframework.security.config.http.SessionCreationPolicy
org.springframework.security.core.annotation.AuthenticationPrincipal
org.springframework.security.authentication.AbstractAuthenticationToken
org.springframework.security.core.authority.AuthorityUtils               // NO_AUTHORITIES
org.springframework.security.core.context.SecurityContextHolder          // getContextHolderStrategy().createEmptyContext()
org.springframework.security.web.SecurityFilterChain
org.springframework.security.web.AuthenticationEntryPoint
org.springframework.security.web.access.AccessDeniedHandler
org.springframework.security.web.authentication.AnonymousAuthenticationFilter
org.springframework.security.web.csrf.CookieCsrfTokenRepository          // final クラス
org.springframework.security.web.csrf.CsrfFilter / CsrfException
org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
org.springframework.security.web.context.RequestAttributeSecurityContextRepository  // 実装クラス（final）
org.springframework.security.web.context.SecurityContextRepository      // saveContext(context, request, response)
org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher     // 旧: MvcRequestMatcher / AntPathRequestMatcher
org.springframework.security.web.util.matcher.RequestMatcher
```

Bean Validation（Hibernate Validator 固有。`spring-boot-starter-validation` に入っている）:

```java
org.hibernate.validator.constraints.time.DurationMin / DurationMax      // Duration の範囲。@DurationMin(seconds = 1) @DurationMax(minutes = 5)
```

Spring Framework / Boot（テストで追加したもの）:

```java
org.springframework.boot.test.context.runner.ApplicationContextRunner     // 設定クラスだけを読み込み、起動の失敗を検証する
org.springframework.test.annotation.DirtiesContext                        // DB コンテナを止めるテストの後始末
org.springframework.boot.test.web.server.LocalServerPort                  // 管理ポートは @Value("${local.management.port}")
```

### 4.3 アノテーション・DSL（使い方まで確認したもの）

| 対象 | 実際に使った形 |
| --- | --- |
| 設定 record | `@ConfigurationProperties(prefix = "quickvote") @Validated record ...`、入れ子 record に `@DefaultValue @Valid`、プリミティブに `@DefaultValue("30")`。有効化は `@ConfigurationPropertiesScan`（`@EnableConfigurationProperties` は使っていない） |
| Security DSL | `http.sessionManagement(s -> s.sessionCreationPolicy(STATELESS))`、`.csrf(csrf -> csrf.spa().csrfTokenRepository(repo))`、`.logout(AbstractHttpConfigurer::disable)`、`.requestCache(c -> c.disable())`、`.headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("...")).referrerPolicy(r -> r.policy(...)))`、`.exceptionHandling(e -> e.authenticationEntryPoint(x).accessDeniedHandler(x))`、`.authorizeHttpRequests(a -> a.requestMatchers("/", ...).permitAll().requestMatchers(EndpointRequest.to("health","metrics")).permitAll().requestMatchers("/api/**").authenticated().anyRequest().denyAll())`、`.securityContext(c -> c.securityContextRepository(repo))`、`.addFilterAfter / addFilterBefore` |
| CSRF（SPA） | `csrf.spa()` は `CookieCsrfTokenRepository.withHttpOnlyFalse()` ＋ SPA 用ハンドラを設定する。**トークンを遅延させず `CsrfFilter` が読み込むので、最初の GET から `XSRF-TOKEN` Cookie が出る（独自のフィルタは要らない。実 HTTP のテストで確認: `RealHttpIT`）。** Secure/SameSite だけ変えたいので、`spa()` のあとに `.csrfTokenRepository(自前のリポジトリ)` で差し替え。`repo.setCookieCustomizer(cookie -> cookie.secure(...).sameSite("Lax"))` |
| 入口の判定 | `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/invites/{token}/accept")` → `matcher.matches(request)`。`request.getRequestURI()` の文字列比較は使わない（5 章の 22） |
| SecurityContext の保存 | 自作の認証フィルタは、`SecurityContextHolder` に置くだけでなく `securityContextRepository.saveContext(context, request, response)` で保存する（5 章の 21） |
| MVC | `@AuthenticationPrincipal VisitorPrincipal`（record を principal にできる）、`@RequestHeader(name = "...", required = false)`、`@PathVariable UUID` |
| JdbcClient | `jdbc.sql("...").param("name", v).query(Long.class).single()` / `.optional()` / `.list()` / `.query((rs, n) -> ...)` / `.query().singleValue()`（`pg_notify` の戻り値 void の列でも動く）/ `.update()`（件数）。`INSERT ... RETURNING id` は `.query(Long.class).single()` |
| トランザクション | `@Transactional(propagation = MANDATORY)`、`@Transactional(readOnly = true, isolation = REPEATABLE_READ)`、`TransactionTemplate.execute / executeWithoutResult`（Boot が Bean を自動構成） |
| スケジューラ | `@EnableScheduling` ＋ `@Scheduled(fixedDelayString = "${quickvote.cleanup.interval:5m}")`（`5m` や `PT5M` の Duration 形式が使える） |
| 起動・停止 | `SmartLifecycle`（`start` / `stop` / `isRunning`）で LISTEN スレッドを管理 |
| テスト | `@SpringBootTest(webEnvironment = RANDOM_PORT)`、`@AutoConfigureMockMvc(print = MockMvcPrint.NONE)`、`@WebMvcTest(Controller.class)` ＋ `@MockitoBean`、`@MockitoSpyBean`、`@ExtendWith(OutputCaptureExtension.class)` ＋ メソッド引数 `CapturedOutput`、`@ActiveProfiles("test")`、`@Import(...)`、`@ParameterizedTest` ＋ `@MethodSource` |
| MockMvcTester | `mvc.post().uri(..).cookie(..).with(csrf()).contentType(..).content(..).exchange()` → `MvcTestResult`。`assertThat(result).hasStatus(HttpStatus.X)`、`.hasStatusOk()`、`.hasContentType(..)`、`.bodyJson().extractingPath("$.code").isEqualTo(..)`、`.bodyText()`。`mvc.method(HttpMethod)` も使える |

### 4.4 設定キー（`application.yaml` 等。動作確認済み）

| キー | 値 | 確認したこと |
| --- | --- | --- |
| `spring.docker.compose.lifecycle-management` | `start-only` | `spring-boot:run` で compose.yaml のDBが起動し、アプリ停止後もDBが残る |
| `spring.docker.compose.enabled` | `false`（prod） | コンテナ内（docker コマンドなし）で起動できる |
| `spring.autoconfigure.exclude` | `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` | 「Using generated security password」のログが消える |
| `spring.threads.virtual.enabled` | `false` | 第36章で true にして観察する想定（このプロジェクトでは false のまま） |
| `spring.jackson.factory.constraints.read.max-document-length` | `16384` | 超えるJSONが400になる（`HttpFlowIT` で確認） |
| `server.shutdown` | `graceful` | SSE接続があると停止が待たされる（9.5 参照） |
| `management.endpoints.web.exposure.include` | `health,metrics` | それ以外は公開されない（`ActuatorIT`） |
| `management.endpoint.health.probes.enabled` | `true` | `/actuator/health/liveness` と `/readiness` |
| `management.endpoint.health.group.readiness.include` | `readinessState,db` | **`probes.enabled` だけでは readiness に `db` は入らない。** DB が止まると readiness が 503（`ReadinessIT`）。liveness は含めない |
| `management.server.port` | `9090`（`application-prod.yaml` のみ） | **管理ポートを設定すると、Actuator のエンドポイントは管理ポートだけで提供され、本体のポートからは 404/401**（`ProductionActuatorIT`。テストは `management.server.port=0` で上書き）。Security の `EndpointRequest` のルールは管理ポートでも効く |
| `spring.task.scheduling.pool.size` | `3` | `@Scheduled` のスレッド数（既定は1）。期限切れ削除とハートビート・掃除が同じスレッドを待ち合わない |
| `server.tomcat.connection-timeout` | `20s` | SSE の書き込みが詰まったとき（読み取りを止めたクライアント）に Tomcat が書き込みを諦める時間。配信スレッドの解放が、これで保証される |
| `management.endpoint.health.show-details` | `never` | 応答に `components` が出ない |
| `logging.structured.format.console` | `ecs` | ECS 8.11 の JSON。MDC の `requestId` と `addKeyValue` の値がトップレベルのフィールドで出る |
| `quickvote.*` | 独自 | `QuickVoteProperties` を参照 |

**存在しなかったキー:** `spring.jackson.datetime.write-dates-as-timestamps`（Boot 4 のメタデータにない）。Jackson 3 では日時は既定で ISO 8601 文字列。

### 4.5 JUnit 6 / Testcontainers まわり

- JUnit は `junit-jupiter` 6.0.3。パッケージ（`org.junit.jupiter.api.*`、`org.junit.jupiter.params.*`）は 5 と同じで、書き方に変更はありません。
- 全テスト共通のタイムアウトは `src/test/resources/junit-platform.properties` の `junit.jupiter.execution.timeout.default = 90s`（SSE の接続待ちなどで固まったときに全体が止まらないため）。
- Testcontainers は `@Testcontainers` / `@Container` を使わず、**`@Bean @ServiceConnection PostgreSQLContainer`** を `@TestConfiguration` に置く方式です。コンテナは ApplicationContext と同じ寿命で、同じ設定のテストクラス間で使い回されます。接続先を `spring.datasource.*` に書く必要はありません（`JdbcConnectionDetails` が差し替わるため、`PollEventListener` の専用接続も追従する）。

---

## 5. 古い世代との違いで、実際にハマった点

執筆者がAPIを捏造しないための記録です。いずれも実際に起きました。

1. **Jackson 3 は record のプリミティブ項目の欠落（と null）を許さない。** `record CreatePollRequest(boolean allowMemberOptions, ...)` で JSON に項目が無いと、`HttpMessageNotReadableException`（400）になりました。素の `JsonMapper` で再現すると `tools.jackson.databind.exc.MismatchedInputException: Cannot map null into type boolean (set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)`。`FAIL_ON_NULL_FOR_PRIMITIVES` が既定で有効で、欠落も null として扱われます。`Boolean` で受けて、コンパクトコンストラクタで「省略 = false」にしています。第13章の「欠落・null」の題材になります。
2. **`@Size` は UTF-16 の char 数を数える。** 絵文字は1文字でも char が2個。「100文字まで」を `@Size(max = 100)` と書くと、絵文字だけの文字列は51文字で弾かれます。`@BoundedText`（コードポイント数）を自作しました。第7章（文字数とバイト数）と第14章の題材。なお上限が「100文字かつ400バイト」でも、コードポイント数が100以下なら UTF-8 は最大でちょうど400バイトで、バイト上限は多重の保険です（`options` の CHECK 制約でも同じ値を使っている）。
3. **`SecurityMockMvcRequestPostProcessors.csrf()` を使うと、同じ `MockMvc` が XSRF-TOKEN の Cookie を発行しなくなることがあった。** テストの実行順によって「GETの応答に XSRF-TOKEN が載らない」失敗が出ました（`csrf()` が共有の CsrfFilter のトークンリポジトリをテスト用に差し替えるためと考えられます。原因の特定まではしていません）。Cookie 属性・CSRF の往復は、MockMvc ではなく **実 Tomcat（`RANDOM_PORT`＋JDK の `HttpClient`）** で確かめています（`RealHttpIT`）。
4. **ユーザーを定義していないのに「Using generated security password」が出る。** `UserDetailsServiceAutoConfiguration` を exclude しないと、使わないパスワードがログに出ます。
5. **`CookieCsrfTokenRepository` は `final`**（Security 7）。継承ではなく `setCookieCustomizer(Consumer<ResponseCookie.ResponseCookieBuilder>)` で属性を変えます。**`csrf.spa()` を使えば、CSRF トークンを読み込むための自作フィルタは要りません**（旧世代の `CsrfTokenRequestAttributeHandler` の遅延トークンのための `CsrfCookieFilter` は、この構成では不要。以前のこのノートの「フィルタがないと最初の GET で Cookie が出ない」は誤りだった）。削除して、最初の GET（静的ファイル・Actuator・401 のAPI・404）で Cookie が出ることを実 HTTP で確認しています。訪問者が新規発行された最初のPOSTの応答では、`XSRF-TOKEN` が再発行（旧値を失効してから新値を発行）されます。クライアントは毎回 Cookie を読み直す必要があります。
6. **自作のSecurityフィルタを `@Component` にすると2回動く。** Boot が Servlet フィルタとしても登録するため。`SecurityConfig` の中で `new` して `addFilterAfter/Before` します。
7. **Security のフィルタの中で出した例外は `@RestControllerAdvice` に届かない。** `HandlerExceptionResolver`（Bean 名 `handlerExceptionResolver`）の `resolveException(request, response, null, exception)` を呼ぶと、`ProblemDetailAdvice` が処理して同じ形式で返せます（401/403/429 に使用）。
8. **PostgreSQL は UNIQUE 違反でトランザクションが aborted になる。** 参加コード衝突のリトライは、同じトランザクション内ではできません。`@Transactional` ではなく `TransactionTemplate` を1試行ごとに使っています（`JoinCodeCollisionIT`）。
9. **`jsonb` は保存時に整形される。** `{"optionId":1}` で保存しても `payload::text` は `{"optionId": 1}`（コロンの後に空白）で返ります。SSE の `data:` もその形です。文字列比較ではなくJSONとして扱ってください。
10. **SSE は何か書き込むまでレスポンスヘッダーが送られない。** `SseEmitter` を返しただけでは、`EventSource.onopen` も JDK の `HttpClient.send` も最初のイベントまで返りません。接続直後に `:connected` のコメント行と `retry:3000` を送っています。
11. **SSE の接続は graceful shutdown を30秒止める。** `server.shutdown=graceful` は処理中のリクエストの完了を待ち、SSE は終わらないリクエストなので、`spring.lifecycle.timeout-per-shutdown-phase`（既定30秒）まで待たされます。`PollEventListener.stop()`（Web サーバーの停止より先に止まる `SmartLifecycle`）で `PollEventHub.closeAll()` を呼び、全ストリームを終了しています。
12. **ProblemDetail の `instance` に既定でリクエストのパスが入る。** 招待トークンはパスに含まれる（`/api/invites/{token}/accept`）ので、応答にもトークンが残ります。`urn:request-id:<相関ID>` に差し替えました（ログと突き合わせて原因を追える）。
13. **`ResponseEntityExceptionHandler#handleExceptionInternal` の `body` は null で渡ることがある**（`NoResourceFoundException` など）。`body instanceof ProblemDetail` だけで `code` を足そうとすると漏れます。基底クラスの結果（`super.handleExceptionInternal` の戻り値）の本文に足します。
14. **入れ子の `@TestConfiguration`（テストクラス内の static class）は「既定の設定クラス」として検出され、Spring Framework 7.0 で警告が出る**（7.1 で挙動が変わる予告）。独立したクラスにして `@Import` します（`TestClockConfig`）。
15. **Java のメソッド名は数字で始められない。** 日本語のテスト名でも `void 30人が...` はコンパイルエラー。
16. **Java の文字列リテラルの `"\s"` は有効なエスケープ（空白）。** 正規表現は `"\\s"`。テストの正規表現で一度ハマりました。
17. **`@WebMvcTest` のスライスでは、本番の `SecurityConfig` は読み込まれない。** Boot 既定のセキュリティ設定が使われるので、`authentication(...)` と `csrf()` のポストプロセッサで通します（`PollControllerValidationTest`）。
18. **Mockito がJDK 25 で「self-attaching」警告を出す**（動作には影響なし）。`-XX:+EnableDynamicAgentLoading` か Mockito をエージェントとして指定すれば消せますが、教材の本筋ではないので放置しています。
19. Maven のビルドログに `flyway-community-db-support` / `MavenFeed` への 401 警告が出ます（Flyway の POM が参照するリポジトリ。取得は成功しており無害）。
20. **`JdbcClient` に `Instant` を直接バインドすると失敗する（検証済み）。** `BadSqlGrammarException`（pgjdbc の "Can't infer the SQL type"）になる。`OffsetDateTime`（UTC）なら通る。だから `Db.ts` で `Instant` を `OffsetDateTime` に変換して渡す方式で統一している。教材の第19章でも、`Instant` を `.param(...)` にそのまま渡さず `Db.ts(...)` を通す理由として、この実測を使える。
21. **自作の認証フィルタは `SecurityContext` をリポジトリに保存しないと、非同期ディスパッチで認証が失われる。** SSE（`SseEmitter`）は接続が終わるとき（タイムアウト・`complete()`・クライアントの切断）、サーブレットコンテナが `DispatcherType.ASYNC` でフィルタチェーンをもう一度通す。`OncePerRequestFilter` は非同期ディスパッチをスキップする（認証フィルタは走らない）のに、`AuthorizationFilter` は Spring Security 6 以降すべてのディスパッチで認可する。ホルダーに置いただけの `SecurityContext` はそのスレッドのその場限りなので、ASYNC ディスパッチでは匿名になり、`AuthorizationDeniedException`（`Servlet.service() ... threw exception`）と「Unable to handle the Spring Security Exception because the response is already committed」「Cannot render error page ... already been committed」が ERROR で出ていた（応答はすでにコミット済みで、401/403 を返すこともできない）。**根本の対処は `securityContextRepository.saveContext(...)`**（`RequestAttributeSecurityContextRepository` でリクエストの属性に保存。セッションは使わない）。`SecurityContextHolderFilter` が ASYNC ディスパッチでそれを読み戻す。`dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()` で黙らせる方法もあるが採らなかった: ASYNC は REQUEST で認可済みの要求の続きなので実害はないが、認可を外すと「認証を失っているのに気づけない」状態が残り、非同期ディスパッチで `@AuthenticationPrincipal` なども使えない。ERROR ディスパッチは `/error` を `permitAll` にしてあり、保存済みの認証も引き継がれる。回帰テストは `RealHttpIT`（サーバーが閉じた場合とクライアントが切った場合の両方で、ログに ERROR が出ない）。

    > **補足（教材第32章の検証で判明）**：現在の構成では、フィルタ列にある `SessionManagementFilter` が「新しく認証された」ことを検知して `SecurityContext` を暗黙に保存するため、`saveContext(...)` の1行だけを消しても `RealHttpIT` は通る（つまりこの1行は回帰テストで守られていない）。`sessionManagement` を無効にした構成では、上記の ERROR 3本がそのまま再現する。ほかのフィルタの暗黙の保存に頼らず、**認証したフィルタ自身が明示的に保存する**のが正しい形なので、実装は `saveContext` を残している。
22. **`request.getRequestURI()` の文字列比較で URL を判定してはいけない。** `getRequestURI()` は生の文字列（`/api/%6aoin`）を返し、Spring MVC は各セグメントをデコードして `/api/join` として処理する。文字列比較のフィルタ（回数制限・訪問者の発行）は、エンコードした URL ですり抜けられる。`PathPatternRequestMatcher`（MVC と同じ PathPattern。デコード後・コンテキストパス除外）で判定する（`EntryMatchers`、`EntryMatchersTest`、`RateLimitIT`、`RealHttpIT`）。`StrictHttpFirewall` は `%6a` のような通常の文字のエンコードは許す（`%2e` `%2f` `%25` `%3b` などは拒否する）。
23. **管理ポートを分けると、Actuator は管理ポートだけになる。** `management.server.port` を本体と別にすると、`/actuator/**` は本体のポートでは提供されない。「本体のポートで health だけ、管理ポートで metrics も」のような使い分けはできない（公開するエンドポイントの指定は1つ）。
24. **`ConcurrentHashMap` の `computeIfAbsent(...).add(...)` は原子的ではない。** 取得した `Set` が、`add` の前に別スレッドの `computeIfPresent`（最後の要素を消して `null` を返す＝キーごと削除）で名簿から外れることがある。取得・作成・追加は `compute` の中で一体にする。同様に、バケットの「消費」と「掃除」も `compute` / `computeIfPresent` の中で行う（バケット内部の `synchronized` では、Map から取り出した後・削除する前の隙間は守れない）。
25. **PostgreSQL の例外メッセージには値が入る。** CHECK 制約違反は `Failing row contains (...)` で行の中身（タイトル・参加コード）が出る。`log.error("...", ex)` は cause のメッセージも出力するので、「自分のメッセージに入力値を連結しない」だけでは漏れる。`SafeThrowable` でメッセージを落としてから渡す。
26. **Spring Framework 7 の `PathPatternParser` に `setMatchOptionalTrailingSeparator` はない**（末尾スラッシュの一致は廃止された）。`/api/join/` は MVC も 404 にし、`PathPatternRequestMatcher` も一致しない。

---

## 6. 主要な設計判断と理由

### 6.1 ロック方式（人数上限・選択肢上限・投票・イベント採番）

- **規則:** 「`polls` 行を `SELECT ... FOR UPDATE` でロックする → 再カウントして判定する → 書き込む」。すべての更新経路（参加・招待受諾・選択肢追加・投票・締切）が同じ規則に従います。
- **理由:** `count` → `insert` の間に別トランザクションが割り込むと、49人のときに2人が同時に「まだ入る」と判断して51人になります。1つの親行を排他ロックして直列化すれば、後続は先行のコミット後に再カウントします。
- **範囲:** ロックは投票ごと。別の投票には影響しません。1投票の同時更新は最大でも参加者50人ぶんなので、直列化のコストは許容しました。
- **ロックの順序:** 常に「冪等性行 → polls 行」の順（デッドロック回避）。招待受諾は「招待を引く（非ロック）→ polls 行ロック → 招待を消費」。
- **1人1票は別の仕組み:** `UNIQUE(poll_id, participant_id)` ＋ `INSERT ... ON CONFLICT (poll_id, participant_id) DO UPDATE`。
  `VoteService` は締切判定とイベント採番のために polls をロックしますが、1人1票そのものはロックではなく制約が守ります。
  ロックを使わず `VoteRepository.upsert` を直接20並列で叩くテストが、それを単独で証明しています。
- **再投票の扱い:** `ON CONFLICT DO UPDATE ... WHERE votes.option_id <> EXCLUDED.option_id`。同じ選択肢への再投票は 0 件更新になり、「票が変わった時だけイベントを出す」判定に使えます（PUT の冪等性の対比）。

### 6.2 親行ロックで足りない条件

- 参加者数・選択肢数は「複数行にまたがる条件」でありDB制約では守れません（アプリのロック＋再カウント）。
- 「投票先の選択肢・参加者は同じ投票のもの」は複合外部キー（`votes (poll_id, option_id) → options (poll_id, id)`）でDBが守ります（`SchemaConstraintIT`）。

### 6.3 冪等性の方式（`IdempotencyService`）

- **方式:** 業務更新と**同じトランザクション**で、`INSERT ... ON CONFLICT DO NOTHING` による「先取り」。
  1. 期限切れの同キー行を削除（定期削除が動く前でも24時間後にキーを再利用できる）
  2. `INSERT ... ON CONFLICT (visitor_id, idem_key) DO NOTHING`。1件挿入できたら自分が最初の実行者 → 業務処理 → 応答JSONを同じ行に `UPDATE`
  3. 0件なら先行が確定済み → `SELECT ... FOR SHARE` して、ハッシュが違えば 422 `IDEMPOTENCY_KEY_REUSED`、同じなら保存済みの応答を返す
  4. **3 で記録が見つからなければ、1 からやり直す**（最大5回）。READ COMMITTED では SQL 文ごとに見える状態が変わるので、「0件だった INSERT」と「SELECT」の間に、定期削除が期限切れの記録を消してコミットすることがある（以前は `IllegalStateException` で 500 だった）。やり直すと、今度は自分が最初の実行者になれる（記録が消えたのは期限切れ＝新しい操作として扱ってよい）。`FOR SHARE` は、読んでいる間に行が消されないようにする（`IdempotencyRaceIT`）
- **同時に同じキー:** 後続の `INSERT ... ON CONFLICT DO NOTHING` は、先行がコミット/ロールバックするまで **PostgreSQL が待たせる**。コミットなら0件（→保存済み応答）、ロールバックなら自分が挿入できる。アプリ側のロックは不要。
- **失敗した試行は保存しない:** 業務エラー（上限超過など）は例外でトランザクションごとロールバックされ、キーの記録も残らない。同じキーで再送すれば再評価される。
- **採らなかった方式:** 別トランザクションで `IN_PROGRESS` 行を先に確定させる方式。クラッシュ後に「記録だけ残り業務更新がない」状態ができ、掃除の仕組みが要るため。
- `Propagation.MANDATORY`: 呼び出し元にトランザクションがなければ例外にして、方式の前提（同一トランザクション）をコードで強制している。
- **リクエストのハッシュ:** `SHA-256(pollPublicId + "\n" + label)`。投票IDを含めるので、別の投票に同じキーを使うと 422。
- **スコープ:** 訪問者 × キー（PK）。別の訪問者が同じキーを使っても干渉しない。
- **保持:** 24時間（`quickvote.idempotency.retention`）。期限を過ぎた行は、同キーの再利用時と定期削除の両方で消える。
- **応答の再現:** 保存するのは応答ボディ（JSON）と HTTP ステータス。再送時は `Idempotent-Replayed: true` を付けて同じ内容を返す。

### 6.4 イベント採番とSSE

- **採番:** `UPDATE polls SET last_event_seq = last_event_seq + 1 ... RETURNING last_event_seq`（polls 行のロック下）。**シーケンスオブジェクトは使わない**: シーケンスは採番順とコミット順が一致しないため、小さい seq のイベントが後から見える（`Last-Event-ID` の補完で取りこぼす）。行ロックはコミットまで続くので、採番順＝コミット順になります。
- **保存:** 業務更新と同じトランザクションで `poll_events` に INSERT し、`pg_notify('poll_events', pollId)` を呼ぶ。`NOTIFY` はコミット時に配送され、ロールバックなら破棄される（テストで確認）。
- **配信:** 各インスタンスの `PollEventListener` が通知を受け、`PollEventHub.onNotify` が購読者ごとに「最後に送った seq より後」を DB から読み直して送る。通知の中身は信用せず DB を正とするので、通知を取りこぼしても次の通知や再接続で追いつける。自インスタンスの変更も他インスタンスの変更も同じ経路。
- **書き込みは専用の配信プール（既定4スレッド）で行う。** `SseEmitter` への書き込みは同期で、読み取りを止めたクライアントに送るとソケットの送信バッファが埋まった時点で止まる。それを LISTEN のスレッドや `@Scheduled` のスレッドでやると、1人の遅いクライアントが全員の配信・ハートビート・期限切れ削除・アプリの停止まで巻き込む（レビュー指摘）。`onNotify` / `deliverAll` / `sendHeartbeats` は「配信してほしい」と頼むだけで即戻る。
  - 購読者ごとに「頼まれ事の数」を数え、0→1 になったときだけ配信プールに仕事を出す（`request` / `drain`）。同じ購読者への配信は同時に1つ（順序が守られ、ロックは不要）、通知が重なっても束ねられ、取りこぼさない。遅い購読者が占有するスレッドは最大1つ。
  - **追いつくまで読み進める:** 履歴は `PAGE_SIZE`（500件）ずつ読み、満杯のページが返るあいだは次の通知を待たずに続きを読む（以前は500件で止まり、501件目は次の通知まで届かなかった）。
  - **詰まった購読者の打ち切り:** 書き込みの前後で `sendingSince` を記録し、ハートビートの定期処理が `sendTimeout`（既定10秒）を超えて書き込み中の購読者を名簿から外す。書き込みが戻ってきたら（成功でも失敗でも）ストリームを `complete()` する。スレッドそのものの解放は Tomcat の書き込みタイムアウト（`server.tomcat.connection-timeout`）に任せる。
  - **停止:** `closeAll()` は接続ごとに別の仮想スレッドで `complete()` を呼び、最大3秒しか待たない（`SseEmitter#complete` は書き込みと同じロックを取るので、詰まった接続の `complete` は戻ってこない）。配信プールは `DisposableBean#destroy` で止める。
- **接続時の順序:** ① 名簿に登録 → ② 再送を頼む。先に登録しておけば、再送中に起きた変更も取りこぼさない（`lastSentSeq` で重複もしない）。登録・解除はどちらも `ConcurrentHashMap#compute` 系（解除と登録が競合して、登録した購読が名簿から消えるのを防ぐ）。
- **`Last-Event-ID` なしの新規接続:** 過去は送らず、現在の最新 seq から。クライアントは `onopen` のたびに詳細を取得し直す（最初の取得と接続の間の隙間を埋める）。
- **`RESYNC`:** クライアントの `Last-Event-ID` がサーバーの最新より大きいとき（DBの作り直し等）に送り、詳細の再取得を促す。履歴の切り詰め（古いイベントの削除）は実装していない。
- **ハートビート:** `:heartbeat` コメント行を15秒ごと（`@Scheduled`。配信プールで送る）。送れなかった購読者は名簿から外す。
- **タイムアウト:** `SseEmitter` は30分で終了。ブラウザの `EventSource` が `retry:3000` に従って再接続し、`Last-Event-ID` で続きから受け取る。
- **LISTEN 接続:** HikariCP のプールとは別の専用接続（`DriverManager` ＋ `JdbcConnectionDetails`）。切断したら2秒後に再接続して `deliverAll()`。切断中に失われた NOTIFY は DB の読み直しで補う（`pg_terminate_backend` で切るテストで確認）。
- **仮想スレッド:** `spring.threads.virtual.enabled=false`（第36章で観察）。配信プールは固定数のプラットフォームスレッド。第36章で仮想スレッドにするなら、配信プールを `Executors.newVirtualThreadPerTaskExecutor()` に替える題材になる（詰まった接続がプールを塞ぐ問題は、スレッドが安くなっても「詰まった書き込み」自体は残る）。

### 6.5 Cookie（匿名セッション）

- `QV_VISITOR`: `SecureRandom` 32バイト → Base64URL（43文字）。DB には SHA-256 の16進（64文字）だけ。
- 属性: `HttpOnly`、`SameSite=Lax`、`Path=/`、`Max-Age=2592000`（30日）。**`Secure` は `quickvote.visitor-cookie.secure` で切替**（開発 false、`prod` true）。実 Tomcat のヘッダーで確認（`RealHttpIT`、`CookieSecureIT`）。
- `HttpSession` は使わない（STATELESS）。再起動しても、複数台でも、同じ訪問者として扱える。
- **新規発行するのは入口の3つだけ**（`POST /api/polls`、`POST /api/join`、`POST /api/invites/{token}/accept`）。他のAPIで Cookie がなければ 401。何でも発行すると、bot が叩くだけで `visitors` が増えるため（`HttpFlowIT`）。入口の判定は `EntryMatchers`（エンコードされた URL でも同じ。5 章の 22）。
- **Cookie の値は形式を先に確かめる**（`Tokens.isWellFormed`: 43文字の Base64URL）。形式が違う値は DB を引かず、「Cookie なし」と同じに扱う。入口では訪問者の行は検証より前に作られるので、不正なリクエストで行が増えないよう、入口ごとに IP 単位の回数制限（6.7）を置いている。
- `last_seen_at` は1時間に1回だけ更新し、そのとき Cookie の有効期限も延ばす（最後のアクセスから30日）。60日使われない訪問者は定期削除する。
- Cookie を消せば権限を失う（再取得できない）。仕様として決めている。
- Spring Security の `AnonymousAuthenticationToken`（何も特定できなかった状態）とは別物で、`VisitorAuthentication` は「Cookie で特定できた訪問者」。

### 6.6 CSRF 設定

- `csrf.spa()`（Security 7）＋ `CookieCsrfTokenRepository`（`XSRF-TOKEN` Cookie、HttpOnly なし）。クライアントは Cookie の値を `X-XSRF-TOKEN` ヘッダーに入れる（実 HTTP で、ヘッダーなし・値違い→403 `CSRF_REJECTED`、一致→成功を確認）。
- 最初のGETで Cookie が出るために独自のフィルタは不要（`csrf.spa()` はトークンを遅延させない）。以前あった `CsrfCookieFilter` は削除した（実 HTTP で確認: `RealHttpIT`）。
- `SameSite=Lax` の Cookie は他サイトからのPOSTで送られないが、CSRF対策は二重にしている（SameSite だけに頼らない）。
- CORS は設定していない（同一オリジンの静的クライアントのみ）。

### 6.7 レート制限（誰でも叩ける入口と、作成系の API）

- **自作トークンバケット**: `ConcurrentHashMap<キー, TokenBucket>`、`TokenBucket` は容量と補充速度、時刻を引数で受け取る（`Clock` から渡す）。補充は「時間を見て計算し直す」方式で、タイマーやスレッドは不要。
- **競合の防ぎ方:** バケットの「消費」も「掃除（削除）」も `ConcurrentHashMap#compute` / `computeIfPresent` の中で行う。キー単位で直列になるので、「掃除が満タンと判定 → 別のリクエストが消費 → 掃除が消費済みのバケットを捨てる → 制限がリセットされる」が起きない。`TokenBucket` 自体は同期しない（`TokenBucketLimiter` が compute の中でしか触らない）。時刻の取得も compute の中で行うので、テストで「消費の最中」を止められる（`TokenBucketLimiterRaceTest`）。
- **対象と軸（`RateLimitFilter` / `RateLimiter`）:**

  | 入口 | IP単位（共通） | 訪問者単位 |
  | --- | --- | --- |
  | `POST /api/join`、`POST /api/invites/{token}/accept` | 容量100・毎分60 | 「試行」容量10・毎分10（参加コード・招待トークンの総当たり対策） |
  | `POST /api/polls`、`POST /api/polls/{pollId}/invites` | 同上 | 「作成」容量10・毎分10 |

  どれも成功・失敗を問わず「試行」として数える。IP の既定値が大きいのは、会場の全員が同じNAT配下から一斉に参加することを想定したため。
- **なぜ訪問者を発行する全入口に IP 単位が要るか:** 訪問者の行は、入力検証・招待検証より前に確定する。以前は `POST /api/join` だけに制限があったので、`POST /api/invites/invalid/accept` や不正な本文の `POST /api/polls` を Cookie を捨てて連打すると、404/400 でも `visitors` の行が増え続けた（60日残る）。CSRF は「他サイトからの書き込み」の対策で、自動化クライアントの資源消費には効かない。
- **位置:** `VisitorAuthenticationFilter` の**前**（`CsrfFilter` の後）。後ろに置くと、Cookie なしで連打する攻撃者が、拒否される前に `visitors` を作り続けてしまう（`RateLimitIT` で、拒否時に行が増えないことを確認）。訪問者の識別には DB を引かず、Cookie 値のハッシュをキーにする。
- **IP単位で拒否したら、そこで終わる。** 続けて訪問者単位のバケットを作ると、Cookie の値を変えながら送るだけで、429 になっているのにバケットがメモリに増え続ける。Cookie の形式（43文字の Base64URL）も、バケットを作る前に確かめる（`RateLimitIT`）。
- **応答:** 429 `RATE_LIMITED` ＋ `Retry-After`（次の1トークンまでの秒数、切り上げ）。
- **掃除:** 満タンに戻ったバケットを1分ごとに捨てる（`evictIdle`）。やらないとキーの数だけメモリが増える。
- **限界（コメントにも明記）:** バケットはJVMのメモリにあるので、**複数台では共有されない**。台数倍の回数まで試せる。共有するなら Redis などが必要（第35章で Bucket4j と併せて触れる）。IPは `getRemoteAddr()`。リバースプロキシの後ろでは `server.forward-headers-strategy` と信頼できるプロキシの設定が要る。

### 6.8 期限切れ削除（スケジューラ）

- 締切（API で拒否）と物理削除（`delete_after`＝作成から7日）は分離。締切後でも物理削除までは残り、削除が止まっていても締切後の投票が通ることはない。
- **件数を区切る:** `DELETE ... WHERE id IN (SELECT id ... ORDER BY delete_after LIMIT :limit)` を1バッチ1トランザクションで。1回の実行は最大 `max-batches-per-run` バッチで打ち切り、残りは次回（再実行しても結果は同じ）。
- **複数台の同時実行防止:** `pg_try_advisory_lock`（セッションレベル）。ロックを取った接続を最後まで握り、同じ接続で `pg_advisory_unlock`。取れなければ待たずにスキップ。
  トランザクションレベル（`pg_try_advisory_xact_lock`）にしなかったのは、バッチごとにトランザクションを分けても全体を1台に限定するため。
- 冪等性記録（24時間）・招待（期限）・未使用の訪問者（60日）も同じ仕組みで削除。

### 6.9 その他の判断

- **詳細取得は `REPEATABLE_READ` の読み取り専用トランザクション。** 投票・選択肢・票・人数・`lastEventSeq` を同じ時点のスナップショットで読むため。`READ COMMITTED` だと、SELECT の間に他人の投票がコミットされて票数と合計がずれて見える。
- **404 と 403 の使い分け:** 参加していない／存在しない → 404（区別不能）。参加者だが権限なし → 403（`HOST_ONLY`、`MEMBER_OPTIONS_DISABLED`）。他の投票の選択肢IDを指定した投票 → 404 `OPTION_NOT_FOUND`。
- **締切の指定:** 投票作成は `closesInMinutes`（整数、5〜10080）で受け取り、サーバーが `Clock` の現在時刻から `closes_at` を計算する。絶対時刻で受け取ると、ブラウザの時計のずれや通信時間のぶん「5分後」が5分を割って拒否される（旧 `closesAt` + 422 `INVALID_CLOSES_AT`）。範囲は現在時刻に依存しない固定の規則なので Bean Validation（400 `VALIDATION_FAILED`、`errors[].field = closesInMinutes`）で検証し、`INVALID_CLOSES_AT` は廃止した。応答の `closesAt` は従来どおり。
- **文字数の単位:** タイトル・選択肢ラベルの「文字数」は **Unicode のコードポイント数**（この仕様での定義。見た目の文字数＝グラフェムではない）。結合文字や ZWJ 絵文字は、見た目が1文字でも複数のコードポイントなので複数の文字として数える。静的クライアントも `maxlength`（UTF-16 の単位）に頼らず、`Array.from(text).length` で検証する。
- **締切後の挙動:** 投票・選択肢追加・新規参加・**招待の受諾**は 409 `POLL_CLOSED`。すでに参加している人の再参加は成功（何も変えない）。冪等性の再送は、締切後でも保存済みの応答を返す。
- **招待の受諾:** 受けた人は HOST。すでに MEMBER なら昇格。人数上限は新規参加のときだけ判定。使用済み・期限切れ・存在しない → すべて 404 `INVITE_INVALID`（理由を区別しない）。**締切後は 409 `POLL_CLOSED`**（投票の顔ぶれを締切後に変えない。参加コードでの参加と同じ規則）。判定は親行ロックの後、招待の消費の後（無効な招待には、投票が締め切られているかを教えないため）。例外でトランザクションがロールバックされるので、**拒否された招待は未消費のまま残る**（締切後は使えないが、使用済みにもならない）。**人数上限で失敗したら消費も取り消される**（同じトランザクション）。招待の受諾は POST（リンクのプレビューや先読みで消費されないため）。招待トークンはURLのフラグメント（`#invite=`）に入れ、サーバーに送らない。
- **参加コード:** 31種×8文字。形式が違うコードは DB を引かず 404。小文字・前後の空白は吸収。コードは主催者にだけ詳細で見せる。
- **ログに出さないもの:** 選択肢ラベル・タイトル・参加コード・トークン・Cookie・Idempotency-Key・リクエスト本文・URL（パターンのみ）・DBエラーのメッセージ（制約違反の値を含みうる）。出すのは許可した項目だけ。検証エラーの応答にも入力値は含めない。
- **例外をそのままログに渡さない。** `log.error("...", ex)` は cause のメッセージも出力する。PostgreSQL の CHECK 制約違反のメッセージには `Failing row contains (...)` で違反した行の中身が入るので、「自分のメッセージに入力値を連結しなければ安全」は誤り（以前のコメントはこの誤りを書いていた）。`SafeThrowable.of(ex)` で、許可リスト方式（例外のクラス名・SQLSTATE・スタックのフレーム）に作り替えてから渡す。`ProblemDetailAdvice`（500）と `ExpiredDataCleaner`（定期削除の失敗）で使う。`LoggingIT` が、実際の CHECK 違反（時計を作成時刻より前に戻して手動締切）と、削除トリガーが秘密を含むメッセージで失敗する状況で確認している。
- **500 の扱い:** 応答はスタックトレースなしの `INTERNAL_ERROR`、ログには例外を1回だけ（`ProblemDetailAdvice` だけで出力。メッセージなし）。
- **Actuator の公開範囲:** 開発は `health,metrics` を本体のポートで公開。**`prod` は管理ポート（9090）だけで提供し、本体のポートからは見えない。** 9090 は compose でも公開しない（`ports` に書かない。ヘルスチェックはコンテナの中から）。「認証あり」で守る方法は採らない: 誰でも訪問者 Cookie を取れる匿名サービスでは、認証済み＝管理者にならない。readiness は `readinessState,db`（DB に届かなければ 503）、liveness は DB を含めない。
- **CSP:** `default-src 'self'; frame-ancestors 'none'`。そのため静的クライアントは inline スクリプト・inline style 属性を使わず、棒の幅も `element.style.width`（CSSOM）で設定している。

---

## 7. 各テストが証明していること

`*Test`（surefire）は Docker 不要、`*IT`（failsafe）は実 PostgreSQL 18（Testcontainers）を使います。件数は最後の `./mvnw verify` の結果です。

### 単体・スライス（`./mvnw test`）

| クラス | 件数 | 証明していること |
| --- | --- | --- |
| `VoteShareCalculatorTest` | 9 | 1/1/1 → 33.4/33.3/33.3、票0件で0除算しない、ランダム2000回で合計がちょうど100.0・各値は理論値との差が0.1未満・scale=1 |
| `JoinCodeGeneratorTest` | 11 | 文字集合は31種で 0/O/1/I/L を含まない、生成は8文字、2万個でほぼ重複なし、正規化、形式判定 |
| `TokenBucketTest` | 5 | 容量まで許可して超過で拒否、拒否時の待ち時間、時間経過での補充、容量を超えて貯まらない、キー独立と満タンバケットの掃除 |
| `TokenBucketLimiterRaceTest` | 2 | **消費の最中に掃除が走っても、消費済みのバケットが捨てられず制限がリセットされない**（Clock を細工して「バケットを手にした直後」で消費を止める）。同じキーへの並列の消費＋掃除でも許可は容量どおり |
| `EntryMatchersTest` | 23 | 入口の判定。`/api/%6aoin`（大文字小文字のエンコード）・コンテキストパス付き・エンコードされた招待 URL でも判定され、メソッド違い・末尾スラッシュ・空のトークン・大文字小文字違いは対象外 |
| `SafeThrowableTest` | 4 | そのままログに出すと原因のメッセージ（違反した行の中身）まで出る前提の確認。作り替えた例外にはメッセージが含まれず、型・SQLSTATE・フレームだけが残る。元の例外は変更されない。循環する原因でも止まる |
| `QuickVotePropertiesTest`（`ApplicationContextRunner`） | 34 | 既定値で起動する。範囲外（0・負の値・上限超過）の設定は起動時に失敗し、どの設定が悪いかが分かる。範囲の境界の値は受け付ける |
| `PollEventHubTest` | 7 | **遅い購読者が詰まっても、他の購読者への配信・`onNotify`・ハートビートは止まらない**。`sendTimeout` を超えた購読者の打ち切り（戻ったらストリームを閉じる）。**停止処理は詰まった接続があっても待ち上限で戻る**。1037件の遅れが次の通知なしで最後まで届く。通知が重なっても順番どおり・重複なし。**登録と最後の解除の競合（20,000回）で登録した購読が消えない**。RESYNC |
| `TokensTest` | 3 | トークンは32バイト乱数の43文字・毎回異なる、SHA-256 が公式テストベクタと一致 |
| `BoundedTextValidatorTest` | 5 | 英数・日本語は100文字まで、絵文字は1文字と数える（`@Size` との違い）、null は検証しない、バイト上限 |
| `PollControllerValidationTest`（`@WebMvcTest`＋`MockMvcTester`） | 10 | 検証エラーが `application/problem+json` で `code=VALIDATION_FAILED`・`errors[].field` つき、入力値を含まない、サービスは呼ばれない、`closesInMinutes` の範囲外・欠落は400、業務エラーが code つきで返る、`Idempotency-Key` 必須、再送ヘッダー |

### 統合テスト（`./mvnw verify`）

| クラス | 件数 | 証明していること |
| --- | --- | --- |
| `PermissionMatrixIT` | 23 | 権限表（非参加者/MEMBER/HOST × GET・選択肢追加・投票・締切・削除・招待発行）が 404/403/成功を返し分ける。存在しない投票と非参加の投票は code・detail まで同一。他投票の選択肢IDは404。招待で共同主催者になれる |
| `ConcurrencyIT` | 8 | **49人の投票に20スレッドが同時参加 → 成功ちょうど1件**（`CountDownLatch` で同時開始）。選択肢8件に20並列追加 → 10件で止まる。同一人物の20並列投票 → 1行。**ロックなしでも UNIQUE＋ON CONFLICT で1行**。30人の同時投票でイベント連番が欠け・重複なし。**同じ招待を10人が同時受諾 → 成功1件**。満員で受諾が失敗したら招待は未使用のまま。満員でも再参加は成功 |
| `IdempotencyIT` | 9 | 同じキーの逐次再送 → 同じ応答・1件のみ・再送ヘッダー・イベントも増えない。**同じキー10並列 → 1件のみで全員同じ応答**。キー再利用で本文違い → 422。別の投票に同じキー → 422。訪問者ごとにスコープ。キー無し/不正 → 400。**24時間の保持期限（Clock を進める）**。失敗した試行は記録されない。非参加者は記録も作れない |
| `DeadlineIT` | 13 | **締切の直前は投票でき、ちょうどから拒否**。締切後の追加・参加も拒否・詳細は CLOSED。手動締切（二重に締めてもイベントは1回、締切時刻も前倒し）。招待は期限ちょうどから無効・直前まで有効。使用済み・存在しない招待は同じ応答。HTTP では 409。**締切後の招待受諾は 409 `POLL_CLOSED`で、招待は未消費のまま（参加者・イベントも増えない）**。手動締切後・すでに参加している人の昇格も拒否。無効な招待は締切後でも `INVITE_INVALID`のまま。招待の「期限の直前まで使える」テストは、24時間後も開いている投票（3日）を使う |
| `PollEventIT` | 16 | 連番つきのイベントが業務更新と同じトランザクションで保存される。同じ選択肢への再投票ではイベントなし。失敗・ロールバックでイベントも採番も残らない。**`Last-Event-ID` より後だけを履歴として取得**。購読時の再送→ライブ配信（重複・欠落なし）。**他インスタンスの書き込みが NOTIFY 経由で届く**（2台構成の再現）。コミットされない NOTIFY は届かない。**LISTEN 接続を強制切断しても再接続して追いつく**。切断された購読者の掃除、ハートビート、停止時の全ストリーム終了、`RESYNC`。**未送信が501件（1ページ500件を超える）・1200件でも、次の通知なしで最後まで再送される**。ハートビートの送信は配信プールで行われるので、待って確かめる |
| `RealHttpIT` | 10 | 実 Tomcat で、CSRF の Cookie＋ヘッダーの往復（ヘッダーなし・値違い→403、一致→201）、実際の Set-Cookie 属性、セキュリティヘッダー、静的クライアントの配信。**SSE が実際にストリーミングされ、接続を切って再接続すると `Last-Event-ID` より後が再送される**。ハートビート。非参加者は404・Cookieなしは401。**独自フィルタなしで、最初のGET（静的ファイル・Actuator・401のAPI・404）に `XSRF-TOKEN` が出る**。`/api/%6aoin` も実 Tomcat で回数制限される。**SSE の接続をサーバーが閉じた場合・クライアントが切った場合の両方で、`AuthorizationDeniedException` などの ERROR がログに出ない** |
| `HttpFlowIT` | 17 | 作成→参加→選択肢追加→投票→締切の通し。Cookie 属性（HttpOnly・SameSite・Max-Age）、トークンがDBに平文で入らない、入口以外では訪問者を発行しない、CSRF なし→403、壊れた JSON・不正なID・未知のパスも ProblemDetail、巨大ボディ→400（`max-document-length`）、検証エラーの形、締切範囲外→422、`instance` が相関IDでトークンを含まない。締切は `closesInMinutes`（範囲外4・0・-5・10081 → 400、省略・文字列 → 400、5分と10080分は通りサーバーの `Clock` から計算された `closesAt`）。**文字数はコードポイント数**（絵文字100個は通り101個は400、ZWJ 絵文字は7コードポイントとして数える） |
| `CleanupIT` | 9 | 7日後に投票と子テーブルが消える、期限内は残る、締切後でも物理削除までは残る、**件数を区切って削除し次回に続きから**、冪等性記録・招待・訪問者の期限切れ削除、**他インスタンスがアドバイザリロックを持つ間はスキップ**、実行後にロックが解放される、4並列で実行しても全件が消える |
| `RateLimitIT` | 14 | 11回目が 429＋`Retry-After`、制限中は正しいコードも通らない、時間経過で補充、訪問者ごとに独立、**Cookie を捨てて連打しても IP 単位で止まり `visitors` の行が増えない**、対象は `/api/join` だけ。**`/api/%6aoin` でも同じバケット**。**Cookie を捨てた不正な招待受諾・検証エラーの投票作成の連打が IP 単位で止まり、`visitors` が増え続けない**。招待の受諾・投票の作成・招待の発行も訪問者単位で制限される。**IP で拒否したら出まかせの Cookie ごとのバケットを作らない**。形式が違う Cookie ではバケットを作らない |
| `SchemaConstraintIT` | 9 | 参加コードの重複・形式違反、空タイトル・不正な状態、二重参加、二重の票、**別の投票の選択肢への投票（複合外部キー）**、**別の投票の参加者を `options.created_by` にした選択肢の INSERT は外部キー違反（V6）**、**投票1を削除しても投票2の選択肢が残る（V6）**、投票削除の CASCADE。アプリの if をすり抜けても DB が拒否する（最終防衛線） |
| `LoggingIT` | 6 | **選択肢ラベル・参加コード・招待トークン・訪問者トークン・Idempotency-Key がログに出ない**、ルートはパターンで出る、相関IDがログと応答ヘッダーで一致・外部IDの引き継ぎ・改行入りIDは捨てて再採番、500でスタックトレースを応答に出さずログに1回だけ。**実際の CHECK 制約違反（時計を作成時刻より前に戻して手動締切）の例外に含まれるタイトル・参加コードがログに出ない（SQLSTATE は出る）**。**期限切れ削除が、秘密を含むメッセージの DB エラーで失敗してもログに出ない** |
| `JoinCodeCollisionIT` | 2 | **参加コードが衝突したら（`@MockitoSpyBean` で強制）別のコードで取り直す**。衝突し続けたら5回で諦め、何も残らない |
| `ActuatorIT` | 3 | health・liveness・readiness は認証なしで UP・詳細なし、metrics 取得可、env・beans・heapdump 等は公開されない |
| `CookieSecureIT` | 1 | `quickvote.visitor-cookie.secure=true` で Cookie に `Secure` が付く |
| `IdempotencyRaceIT`（`@MockitoSpyBean`） | 2 | **先取りが0件だった直後、記録を読む前に別接続で記録が削除・コミットされても、500にならず先取りからやり直す**（読む直前にフックして削除を挟む）。記録が消えていなければ従来どおり保存済みの応答 |
| `ReadinessIT`（DB コンテナを停止。専用コンテキスト） | 1 | **DB が止まると readiness が 503 / DOWN、liveness は UP のまま** |
| `ProductionActuatorIT`（`prod` プロファイル、管理ポートはランダム） | 4 | 管理ポートは本体と別。**本体のポートでは health も metrics も見えない**。管理ポートでは health と metrics だけ見え、env・beans・heapdump などは見えない。prod の Cookie に Secure が付く |

統合テストの土台（`support/`）: `AbstractIntegrationTest`（共通設定・テスト間のDB初期化・HTTPヘルパー）、`TestClockConfig`＋`MutableClock`（時間を進める）、`Concurrently`（全スレッドを揃えて一斉開始。`CountDownLatch` 2つ）。
テスト間の独立性は、`@BeforeEach` の `TRUNCATE ... RESTART IDENTITY CASCADE` と時計の巻き戻しで確保しています。レート制限のバケットだけはアプリのメモリにあるので、各テストが固有のIPと訪問者を使います（`AbstractIntegrationTest` が `@BeforeEach` でテストごとの接続元IP `clientIp` を作り、HTTP ヘルパーがそれを使う）。Spring を起動しない単体テストの設定値は `support/TestProperties`。

---

## 8. 実行したコマンドと結果

環境: Windows 10、JDK 25.0.5（Eclipse Adoptium）、Docker Desktop（Docker 29.9.2）、Maven 3.10.0（Wrapper）。

| コマンド | 結果 |
| --- | --- |
| `./mvnw verify` | **BUILD SUCCESS**。surefire 113件 ＋ failsafe 147件 ＝ **260件、失敗0・スキップ0**（V6 と SchemaConstraintIT 2件の追加後に再実行）（所要 約4分。DB のコンテナを止める `ReadinessIT` と、専用コンテキストの IT が増えたぶん長くなった）。ログの ERROR は意図した3件だけ（`LoggingIT` の 500・CHECK 制約違反・削除失敗）。`AuthorizationDeniedException` などは出ない |
| `./mvnw test` | 113件 成功（Docker 不要） |
| `./mvnw spring-boot:run` | compose.yaml の PostgreSQL 18 が自動起動し、約15秒でアプリが起動。ブラウザで 作成 → 参加（別セッションから curl）→ 投票 → **得票バーがリアルタイムに更新** → 選択肢追加 → 招待リンク発行 → 招待受諾 を確認 |
| `docker build -t quickvote .` | 成功（マルチステージ、layered jar、非root `app` ユーザー） |
| `docker compose -f compose.app.yaml up --build` | app・postgres ともに **healthy**（healthcheck は管理ポート 9090 の readiness）。`prod` プロファイルで起動、非root（uid 999）で動作、投票作成が 201、Secure を false に戻した設定で Cookie が発行される。アプリのメモリは約380MiB。**公開した本体のポートでは `/actuator/health`・`/actuator/metrics` が 401、ホストから 9090 には届かない。コンテナの中からは 9090 の readiness・metrics が 200**（他の担当と衝突しないよう、ホスト側は 18080 に付け替えて確認） |
| 静的クライアントのブラウザ確認（18080） | 絵文字100個のタイトルと「5分後」で作成できる（`maxlength` なし）、101個は画面で拒否される。詳細取得を遅延させた応答で、同時取得は1つ・通知は1回にまとめられる・古い応答（seq 10 が 11 の後）は捨てられる・ホームへ戻った後に届いた応答は描画されない |
| 2インスタンス（8080・8081、同じDB）の手動確認 | 8081 に SSE を接続し、8080 で投票 → 8081 の SSE に `VOTE_CAST` が届いた |

---

## 9. 既知の限界・妥協点・未実装

教材で「ここは本番では…」と触れる題材でもあります。

1. **レート制限は台数倍に緩む**（メモリ内）。共有する実装（Redis・DB）は未実装。IP は `getRemoteAddr()` で、プロキシ配下の `X-Forwarded-For` は未設定（コメントのみ）。NAT 配下の全員は同じ IP のバケットを共有する（容量100・毎分60。会場の人数を超える使い方では `quickvote.rate-limit.ip-*` を上げる）。
2. **イベント履歴は削除しない**（投票と一緒に CASCADE で消える）。`RESYNC` は「クライアントがサーバーより先に進んでいる」場合のみで、「履歴が切り詰められた」場合の判定は未実装。
3. **投票の削除は SSE 購読者に通知しない。** 購読者は次の接続の失敗（404）で気づく。
4. **SSE の書き込みが詰まったとき、スレッドを解放するのは Tomcat の書き込みタイムアウト。** 配信は専用プールで行い、詰まった購読者は `sendTimeout`（10秒）で名簿から外すが、ブロッキングの書き込み自体を中断する手段は（`SseEmitter` にも `complete()` が同じロックで待たされるため）ない。詰まったスレッドが戻るのは `server.tomcat.connection-timeout`（20秒）後。**同時に詰まった接続が配信スレッド数（4）を超えると、その間は配信が遅れる。** 参加者（認証済みの訪問者）だけが購読できる前提での割り切り。ノンブロッキングの書き込み（WebFlux / 非同期サーブレット）は対象外。
5. **Actuator は prod では管理ポート（9090）だけで、認証なし。** 管理ポートへの到達をネットワークで内部に限ることが前提（compose では公開していない）。管理ポートに `management.server.address` を指定してループバックに束縛する方法もあるが、監視システムが別コンテナから収集できなくなるので採らなかった。開発プロファイルは本体のポートで `health,metrics` を無認証で公開している（ローカル専用）。第44章で触れる。
6. **招待の発行数に上限がない**（主催者は何本でも発行できる。期限切れは定期削除）。
7. **リクエストサイズ上限は JSON の `max-document-length`（16KB）のみ。** Tomcat のヘッダーサイズ等は既定値のまま（第35章・第45章）。
8. **「自分の投票一覧」API はない。** 静的クライアントが `localStorage` に開いた投票を記憶する（Cookie を消すと権限も失う仕様）。
9. **`participants.revoked_at` を設定する API（参加者の除名）はない。**
10. **ログは常にECS形式のJSON。** 開発中は読みにくい。`logging.structured.format.console` を空にすれば通常の形式に戻せる（教材では第43章で扱う）。
11. 静的クライアントは最小限（エラー表示は1か所のメッセージのみ、アクセシビリティの作り込みなし）。ブラウザでの手動確認はデスクトップ幅のみ。
12. JPA 版（第8部）は別プロジェクト（`code/jpa-lab/`）の担当で、このプロジェクトには含まない。
13. **静的クライアントの JavaScript は自動テストがない**（ブラウザで手動確認: 詳細取得の応答順の入れ替わり・コードポイント数の検証・締切の分数）。サーバー側の検証（`BoundedText`・`closesInMinutes`）は自動テストで見ている。
14. `@Scheduled` の `fixedDelay` のずれ、Windows 以外の OS でのコマンドは未検証（コマンドは Windows の Git Bash / PowerShell で実行して確認）。

---

## 10. シニアレビューへの対応（変更点）

`production/design/review-codex.md` の17件＋監督が見つけた1件（ログの ERROR）をすべて反映した。API が変わった点は **太字**。

| # | 指摘 | 対応 | 回帰テスト |
| --- | --- | --- | --- |
| 1 | URL エンコードで回数制限をすり抜けられる | `request.getRequestURI()` の文字列比較をやめ、`PathPatternRequestMatcher`（`EntryMatchers`）で判定。回数制限と訪問者の発行が同じ判定を共有 | `EntryMatchersTest`、`RateLimitIT`（`/api/%6aoin`）、`RealHttpIT`（実 Tomcat） |
| 2 | 例外をそのままログに渡し、DB エラーの入力値が漏れる | `SafeThrowable`（型・SQLSTATE・フレームだけ。メッセージなし）を `ProblemDetailAdvice` と `ExpiredDataCleaner` で使う。「連結しなければ安全」のコメントを訂正 | `SafeThrowableTest`、`LoggingIT`（実際の CHECK 違反・削除トリガー） |
| 3 | 不正な作成・受諾で訪問者の行を増やせる | 訪問者を発行する全入口（作成・参加・招待の受諾）に、発行前の IP 単位の回数制限。作成・招待発行には訪問者単位の制限も追加（`create-*`）。招待の受諾にも訪問者単位 | `RateLimitIT` |
| 4 | SSE の登録と最後の解除が競合し、新しい購読が消える | 登録を `subscriptions.compute(...)` の中でひとまとめに（解除側と同じキー単位の直列化） | `PollEventHubTest`（20,000回の同時開始。旧実装では失敗することを確認） |
| 5 | 未送信が500件を超えると再送が止まる | 満杯のページが返るあいだ、次の通知を待たず読み進める（`PAGE_SIZE`） | `PollEventIT`（501件・1200件）、`PollEventHubTest` |
| 6 | IP 制限で拒否した後も、偽 Cookie ごとにバケットを作る | IP 単位で拒否したら即終了。Cookie の形式（43文字の Base64URL）を先に検査 | `RateLimitIT`、`RateLimiter` のバケット数で検証 |
| 7 | 掃除と消費が競合し、消費済みバケットを捨てて制限がリセットされる | 消費も掃除も `compute` / `computeIfPresent` の中で | `TokenBucketLimiterRaceTest`（消費の最中に掃除を走らせる。旧実装では失敗することを確認） |
| 8 | 冪等性記録が削除されると、再送が500になる | `FOR SHARE` で読み、記録がなければ先取りからやり直す（最大5回） | `IdempotencyRaceIT`（読む直前に別接続で削除してコミット） |
| 9 | 遅い SSE 接続が他の通知・定期処理・停止を止める | 書き込みを専用の配信プールへ。購読者ごとに直列・束ねる。`sendTimeout` で打ち切り。`closeAll` は接続ごとの仮想スレッドで、待ち上限3秒。`@Scheduled` のスレッドを3本に | `PollEventHubTest`（詰まった購読者・打ち切り・停止） |
| 10 | 詳細取得の応答順が逆転すると古い状態を描画する | クライアント: 同時に取得は1つ（通知はまとめる）、画面の世代と `lastEventSeq` で古い応答を捨てる | 手動（ブラウザ） |
| 11 | readiness が DB を確認していない | `management.endpoint.health.group.readiness.include=readinessState,db` | `ReadinessIT`（DB コンテナを停止） |
| 12 | 本番で metrics が無認証で公開される | `prod` は `management.server.port=9090`。Actuator は管理ポートだけ。compose / Dockerfile は 9090 を公開しない | `ProductionActuatorIT` |
| 13 | 独自の CSRF Cookie フィルタが不要 | **削除**（実 HTTP で、最初の GET に Cookie が出ることを確認できた）。レート制限フィルタは `CsrfFilter` の後ろに配置 | `RealHttpIT` |
| 14 | 「5分後」が通信時間のぶん短くなって拒否される | **`closesInMinutes`（整数 5〜10080）で受け取り、サーバーが `Clock` から計算。** `closesAt` の受け取りと `INVALID_CLOSES_AT`（422）は廃止（範囲外は 400 `VALIDATION_FAILED`）。静的クライアントも合わせた | `HttpFlowIT`、`PollControllerValidationTest` |
| 15 | コードポイント数の100文字制限と `maxlength` が不一致 | **文字数＝コードポイント数と仕様に明記。** クライアントは `maxlength` をやめ、`Array.from` で検証。「人間が数える文字＝コードポイント」の説明を削除し、結合文字・ZWJ 絵文字は複数に数えると訂正 | `BoundedTextValidatorTest`（既存）、手動（ブラウザ） |
| 16 | 期間設定の検証がなく、不正値で起動が落ちない | `@DurationMin` / `@DurationMax` / `@Min` / `@Max` で許容範囲を宣言（`0s`・負の値は起動時に拒否） | `QuickVotePropertiesTest` |
| 17 | 締切後の招待受諾の仕様が不明 | **締切後は 409 `POLL_CLOSED`。** 親行ロックの後で判定。拒否時は消費がロールバックされ、招待は未消費のまま | `DeadlineIT` |
| 18 | SSE 終了時に `AuthorizationDeniedException` が ERROR で出る | 認証フィルタが `SecurityContext` を `RequestAttributeSecurityContextRepository` に**保存**する（ASYNC ディスパッチで読み戻される）。理由は 5 章の 21 | `RealHttpIT`（サーバーが閉じた場合・クライアントが切った場合） |

### 設計判断の補足

- **12 の「health のみ」と「metrics は管理ポート」の両立:** Boot では管理ポートを分けると、公開するエンドポイントはすべて管理ポートだけになる。本体のポートに health だけ残す構成はできないので、「本体のポートでは Actuator を一切出さず、管理ポート（内部専用）で health と metrics」とした。ロードバランサーやコンテナのヘルスチェックは 9090 に向ける。
- **13 を削除した根拠:** `csrf.spa()` は CSRF トークンを遅延させない。旧世代（`CsrfTokenRequestAttributeHandler`）では、遅延トークンを読むためのフィルタが必要だった。静的ファイル・Actuator・401 の API・404 の最初の GET のすべてで Cookie が出ることを実 Tomcat で確認してから削除した。
- **15 の文字数:** 「見た目の文字数」を数えるにはグラフェムの分割（ICU など）が要り、DB の `char_length` もコードポイント数なので、アプリ・クライアント・DB の三者が同じ単位（コードポイント）で揃う。
- **17 の判定順:** 招待の消費（無効なら 404 `INVITE_INVALID`）の後に締切を判定する。順序を逆にすると、使用済み・期限切れの招待を持つ人に、投票が締め切られているかどうかを教えてしまう。

---

## 11. 追加修正の履歴（変更履歴）

シニアレビュー対応（10 章）の後に、教材の執筆者（第18章・第19章）が見つけた点を直した。

| # | 内容 | 対応 | 確認 |
| --- | --- | --- | --- |
| 追加1 | **`options.created_by` の外部キーが単独列。** V2 は `REFERENCES participants (id)` だけだったので、別の投票の参加者を作成者にした選択肢が入り、投票1を削除すると投票2の選択肢まで連鎖して消えた（第18章の執筆者が psql で実証） | 適用済みの V2 は書き換えず、**`V6__options_created_by_composite_fk.sql` を追加**。単独の外部キーを削除し、`FOREIGN KEY (poll_id, created_by) REFERENCES participants (poll_id, id) ON DELETE CASCADE` に置き換え（参照先の `UNIQUE (poll_id, id)` は V2 に既にある）。既存データに食い違う行があると失敗すること、洗い出しの SQL は V6 のコメントに書いた | `SchemaConstraintIT` に2件追加（別の投票の参加者を作成者にした INSERT が拒否される／投票1の削除で投票2の選択肢が残る） |
| 追加2 | README が「`TestQuickvoteApplication` の `main` で Testcontainers の使い捨てDBで起動する」と書いていたが、実際は違った | 実際に `./mvnw spring-boot:test-run` で起動して確認: **`spring-boot-docker-compose` が有効だと `compose.yaml` の DB（15432）が起動してアプリはそちらにつながる。Testcontainers のコンテナも起動するが使われない。** `--spring.docker.compose.enabled=false` を付けると Testcontainers の DB だけになり、アプリを止めるとコンテナも消える。README を実際の挙動に合わせて書き直した（この引数が必要） | 手動（18099 で起動し、接続先のコンテナの `flyway_schema_history` を確認。確認後に停止） |
| 追加3 | 5 章の項目20（`Instant` の直接バインドは未検証）が未検証のまま | 第19章の執筆者の実測を反映: `BadSqlGrammarException`（"Can't infer the SQL type"）で失敗、`OffsetDateTime` なら通る。9 章の未検証項目からも外した | 執筆者の実測（このプロジェクトでは `Db.ts` が変換している） |

## 12. 教材の執筆中に実測で見つかった既知の限界（コードは意図的に変えていない）

教材の第9部・第3部の執筆者が実際に動かして見つけたもの。本文で「実測で見つけた穴」「演習」として扱っているため、本文との整合を保つ目的でコードは変えていない。自分のサービスでは最初から対処すること。

| # | 内容 | 教材での扱い |
| --- | --- | --- |
| 1 | フィルタ内で起きたDB例外（DB停止中の `VisitorAuthenticationFilter`）は `ProblemDetailAdvice` も `SafeThrowable` も通らず、Tomcat が生のメッセージで ERROR を出す。`requestId` も付かず、応答は Boot 既定のエラーJSON | 第43章 |
| 2 | トランザクション管理（`TransactionInterceptor` 等）がロールバック時の例外メッセージを ERROR で出し、同じ失敗が二重に記録される | 第43章 |
| 3 | `compose.app.yaml` に `mem_limit` がなく、`MaxRAMPercentage=75` のヒープ上限が Docker VM 全体の75%になる | 第42章 |
| 4 | `PollRepository` の `SELECT *` が、稼働中の `ADD/DROP COLUMN` 直後に「cached plan must not change result type」で1回失敗し得る | 第46章 |
| 5 | Hikari の `maximum-pool-size=2` では Flyway が接続を2本使うため起動できない | 第24章・第45章 |
| 6 | `statement_timeout` 未設定。ロック待ち・接続待ちは500 `INTERNAL_ERROR` になり、readiness の503まで30秒かかる | 第45章 |
| 7 | `ExpiredDataCleaner` は削除0件でも毎回 INFO を出す（軽微） | — |
| 8 | 投票作成（`POST /api/polls`）には `Idempotency-Key` がなく、応答喪失後の再送で投票が二重にできる（被害の小ささから割り切り） | 第27章・第48章 |
| 9 | 期限切れ削除は4種の削除を直列に行うため、投票の削除が失敗し続ける行があると他の掃除もその回は止まる | 第29章 |
