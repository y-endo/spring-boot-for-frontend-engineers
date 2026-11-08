# QuickVote

会議や飲み会で「今この場で決める」ための、会員登録不要の匿名投票サービスです。
このリポジトリは、教材「サーバーが守るべき約束を決め、実装し、壊して確かめる Java/Spring 教材」の通しの題材（リファレンス実装・完成形）です。

- 主催者が投票を作り、参加者は8文字の参加コードで参加します。
- 参加者は選択肢を追加でき（主催者の設定で可否）、1人1票で投票します（再投票は上書き）。
- 得票は SSE（Server-Sent Events）でリアルタイムに伸びます。
- 主催者は共同主催者の招待リンク（一度限り）を発行できます。
- 締切を過ぎると投票は API で拒否され、作成から7日で物理削除されます。

教材の執筆者は、先に [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) を読んでください。

## 技術構成

| 項目 | 内容 |
| --- | --- |
| Java | 25（LTS） |
| Spring Boot | 4.1.2（Spring Framework 7 / Spring Security 7 / Jackson 3 / JUnit 6） |
| Web | Spring MVC |
| データアクセス | JdbcClient ＋ 手書きSQL（JPA は使いません） |
| DB | PostgreSQL 18、マイグレーションは Flyway |
| ビルド | Maven ＋ Maven Wrapper（`./mvnw`） |
| テスト | JUnit Jupiter 6、AssertJ、MockMvcTester、Testcontainers（実 PostgreSQL） |
| クライアント | ビルド不要の静的HTML（`src/main/resources/static`） |

## 前提

- JDK 25（`java -version` が 25 であること）
- Docker Desktop（起動していること。開発用DBとテストで使います）

## 起動方法

開発用のPostgreSQLは Spring Boot が `compose.yaml` から自動で起動します（`spring-boot-docker-compose`）。
先に `docker compose up` を実行する必要はありません。

### Mac / Linux

```bash
./mvnw spring-boot:run
```

`Permission denied` になる場合は、実行権限を付けます（Windows で作ったファイルを Git 経由で受け取ったとき）: `chmod +x mvnw`。

### Windows（PowerShell）

```powershell
# JDK 25 が PATH にない場合のみ（インストール先は環境に合わせて変更）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\mvnw.cmd spring-boot:run
```

起動したら <http://localhost:8080> を開きます。投票を作り、表示された参加コードを別のブラウザ（またはシークレットウィンドウ）で入力すると、別の参加者として参加できます。
Cookie が訪問者の識別に使われるので、同じブラウザの別タブは同じ訪問者になります。

- アプリを止めても、開発用DBのコンテナは動いたままです（`lifecycle-management: start-only`）。
  止めるには `docker compose down`（データも消すなら `docker compose down -v`）。
- DB には `docker compose exec postgres psql -U quickvote` で入れます（ホスト側のポートは 15432）。

### 使い捨てDB（Testcontainers）で起動する

`src/test/java/.../TestQuickvoteApplication` の `main` は、Testcontainers の PostgreSQL をつないでアプリを起動するための入口です。
ただし**そのまま実行しても使い捨てDBにはなりません。** `spring-boot-docker-compose` が有効なままだと、
`compose.yaml` の PostgreSQL（ポート 15432）が先に起動して**そちらがアプリの接続先になります**
（Testcontainers のコンテナも起動はしますが、アプリは使いません。DB の中身は残り続け、`docker compose down` するまで compose のコンテナも残ります）。
使い捨てDBにするには、compose 連携を切る引数 **`--spring.docker.compose.enabled=false` が必要**です。

```bash
# コマンドラインから（Windows PowerShell は .\mvnw.cmd）
./mvnw spring-boot:test-run -Dspring-boot.run.arguments="--spring.docker.compose.enabled=false"
```

IDE からは、`TestQuickvoteApplication` の実行構成の「プログラム引数」に `--spring.docker.compose.enabled=false` を入れて `main` を実行します。
この引数を付けると、起動するのは Testcontainers の PostgreSQL 18 だけで（Flyway の V1〜V6 もそこに適用されます）、
アプリを止めるとコンテナも消えます（DB のポートはランダム）。アプリのポートを変えるなら `--server.port=18099` も同じ引数に並べます。

### コンテナで動かす（アプリ＋DB）

```bash
docker compose -f compose.app.yaml up --build
```

`prod` プロファイル（Cookie の Secure 属性が有効）で起動します。`compose.app.yaml` は HTTP（localhost）で試せるよう、Secure だけ false に戻しています。
HTTPS で公開する場合は、その行を削除してください。

公開するポートは利用者向けの 8080 だけです。`prod` では Actuator（health と metrics）は別の管理ポート 9090 で動き、
compose では外部に公開していません（コンテナの外からは見えません。ヘルスチェックはコンテナの中から 9090 に問い合わせます）。
metrics は稼働状況・資源使用量が分かるので、利用者が届く場所に出さない構成にしています。

### 2台構成を試す

同じDBに2つのインスタンスをつなぐと、「隣のサーバーにつないだ人にも届く」ことを確認できます
（PostgreSQL の LISTEN/NOTIFY でインスタンス間に通知します）。

```bash
./mvnw -q -DskipTests package
docker compose up -d
ARGS="--spring.docker.compose.enabled=false --spring.datasource.url=jdbc:postgresql://localhost:15432/quickvote --spring.datasource.username=quickvote --spring.datasource.password=quickvote"
java -jar target/quickvote.jar $ARGS --server.port=8080 &
java -jar target/quickvote.jar $ARGS --server.port=8081 &
```

8080 で投票を作り、8081 の画面でも同じ投票を開いて、一方で投票するともう一方の画面が更新されます
（Cookie はポートを区別しないので、同じ訪問者として両方を使えます）。

## テスト方法

```bash
./mvnw test      # 速いテストだけ（単体テスト・@WebMvcTest）。Docker 不要
./mvnw verify    # すべて（Testcontainers の統合テスト *IT を含む）。Docker 必須
```

Windows は `.\mvnw.cmd` に読み替えてください。1クラスだけ動かすには:

```bash
./mvnw verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ConcurrencyIT
```

`*Test` は surefire（`test` フェーズ）、`*IT` は failsafe（`verify` フェーズ）が実行します。初回は PostgreSQL のイメージ取得のため時間がかかります。

## 構成

```
src/main/java/com/example/quickvote/
├── common/        例外→ProblemDetail、冪等性、設定値、相関ID、入力検証、時刻・トークンの部品
├── poll/          投票の作成・取得・締切・削除、参加コード、得票率の計算、期限切れ削除
├── participant/   参加者（HOST / MEMBER）と認可の入口
├── vote/          投票（1人1票・上書き）
├── invite/        共同主催者の招待（一度限り）
├── event/         SSE、イベント履歴、LISTEN/NOTIFY
└── security/      匿名セッション、Spring Security 設定、CSRF、試行回数制限
src/main/resources/
├── db/migration/  Flyway（V1〜V6）
└── static/        静的クライアント（index.html / app.js / app.css）
```

各クラスの役割、設計判断、Spring Boot 4 / Spring Security 7 / Jackson 3 で実際に確認した API は
[IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) にあります。

## API

すべて `/api` 配下で、JSON を扱います。エラーは ProblemDetail（`application/problem+json`）で、
業務エラーには機械判定用の `code` プロパティが付きます。
更新系（POST / PUT / DELETE）は CSRF 対策のため、`XSRF-TOKEN` Cookie の値を `X-XSRF-TOKEN` ヘッダーに入れて送ります
（静的クライアントは自動で行います）。

| メソッド・パス | 説明 | 権限 | 成功時 |
| --- | --- | --- | --- |
| `POST /api/polls` `{title, closesInMinutes, allowMemberOptions, options}` | 投票を作る（作成者は HOST）。締切は「今から何分後か」（整数 5〜10080）で指定し、サーバーが締切時刻を計算する | 誰でも（訪問者 Cookie を発行。IP 単位・訪問者単位の回数制限） | 201 |
| `POST /api/join` `{code}` | 参加コードで参加 | 誰でも（試行回数制限）。締切後の新規参加は 409 | 200 |
| `GET /api/polls/{pollId}` | 詳細（選択肢・得票・得票率・自分の票） | 参加者 | 200 |
| `POST /api/polls/{pollId}/options` `{label}` | 選択肢追加（`Idempotency-Key` ヘッダー必須） | HOST、または許可時の MEMBER | 201 |
| `PUT /api/polls/{pollId}/my-vote` `{optionId}` | 投票（上書き） | 参加者・締切前 | 200 |
| `POST /api/polls/{pollId}/close` | 締切を早める | HOST | 204 |
| `DELETE /api/polls/{pollId}` | 削除 | HOST | 204 |
| `POST /api/polls/{pollId}/invites` | 共同主催者の招待を発行 | HOST（回数制限） | 201 |
| `POST /api/invites/{token}/accept` | 招待を受ける。**締切後は 409 `POLL_CLOSED`（招待は消費されない）** | 誰でも（一度限り・24時間。試行回数制限） | 200 |
| `GET /api/polls/{pollId}/events` | SSE（`Last-Event-ID` 対応） | 参加者 | 200 |

「存在しない投票」と「参加していない投票」は、どちらも 404 `POLL_NOT_FOUND` です（存在を漏らしません）。

リクエストの主な制約: 投票のタイトルは1〜100文字、選択肢ラベルは1〜100文字かつ UTF-8 で400バイト以内。
締切（`closesInMinutes`）は整数で5〜10080（5分〜7日）。参加者は最大50人、選択肢は最大10件。応答の `closesAt` は ISO 8601 の UTC。

**文字数は Unicode のコードポイント数で数えます**（この仕様での「1文字」の定義）。見た目の文字数（グラフェム）ではありません。
`String.length()` や HTML の `maxlength`（UTF-16 の単位）とも違い、サロゲートペアの絵文字は1文字ですが、
結合文字（「か」＋濁点など）や ZWJ でつないだ絵文字（家族の絵文字など）は、見た目が1文字でも複数のコードポイントなので、複数の文字として数えます。
静的クライアントも、`maxlength` に頼らず、コードポイント数で検証しています。

### エラーコード

| HTTP | `code` | 意味 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 入力検証エラー（`errors` に項目名とメッセージ。`closesInMinutes` が5〜10080の範囲外・欠落もここ） |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `Idempotency-Key` がない、または形式が不正 |
| 400 | `BAD_REQUEST` ほか | JSON の形式不正など、Spring MVC が検出したもの（HTTPステータス名） |
| 401 | `UNAUTHENTICATED` | 訪問者 Cookie がない、または無効 |
| 403 | `HOST_ONLY` | 主催者のみの操作 |
| 403 | `MEMBER_OPTIONS_DISABLED` | この投票では参加者の選択肢追加が許可されていない |
| 403 | `CSRF_REJECTED` | CSRF トークンが正しくない |
| 404 | `POLL_NOT_FOUND` | 投票がない、または参加していない |
| 404 | `OPTION_NOT_FOUND` | この投票の選択肢ではない |
| 404 | `JOIN_CODE_NOT_FOUND` | 参加コードが存在しない（形式不正も同じ） |
| 404 | `INVITE_INVALID` | 招待が無効・期限切れ・使用済み（区別しない） |
| 409 | `POLL_FULL` | 参加者が上限 |
| 409 | `OPTION_LIMIT_REACHED` | 選択肢が上限 |
| 409 | `POLL_CLOSED` | 締切後（投票・選択肢追加・新規参加・招待の受諾） |
| 422 | `IDEMPOTENCY_KEY_REUSED` | 同じ `Idempotency-Key` が別の内容で使われた |
| 429 | `RATE_LIMITED` | 試行回数の超過（`Retry-After` ヘッダー付き）。対象は参加・招待の受諾・投票の作成・招待の発行 |
| 500 | `INTERNAL_ERROR` | 想定外のエラー（スタックトレースは返さない） |

### SSE のイベント

`GET /api/polls/{pollId}/events` は `text/event-stream` を返します。イベントの種類は
`PARTICIPANT_JOINED`、`OPTION_ADDED`、`VOTE_CAST`、`POLL_CLOSED`、`RESYNC` で、`id:` は投票ごとの連番です。
ブラウザの `EventSource` は切断されると自動で再接続し、最後に受け取った `id` を `Last-Event-ID` ヘッダーで送るので、
サーバーはそれより後のイベントを再送します。`RESYNC` を受け取ったら、詳細を取得し直してください。

## 設定

`src/main/resources/application.yaml` の `quickvote.*`（運用で変えたい値だけ）:

| キー | 既定値 | 意味 |
| --- | --- | --- |
| `quickvote.visitor-cookie.secure` | `false`（`prod` は `true`） | 訪問者 Cookie と CSRF Cookie の Secure 属性 |
| `quickvote.rate-limit.ip-capacity` / `ip-refill-per-minute` | 100 / 60 | IP単位のトークンバケット。訪問者を発行する入口（投票の作成・参加・招待の受諾）と招待の発行で共通 |
| `quickvote.rate-limit.visitor-capacity` / `visitor-refill-per-minute` | 10 / 10 | 訪問者単位。参加コード・招待トークンを試す回数（`POST /api/join`、招待の受諾） |
| `quickvote.rate-limit.create-capacity` / `create-refill-per-minute` | 10 / 10 | 訪問者単位。投票の作成・招待の発行の回数 |
| `quickvote.sse.heartbeat-interval` / `timeout` | 15s / 30m | SSE のハートビート間隔（1秒〜5分）と接続の最大時間（1分〜24時間） |
| `quickvote.sse.send-timeout` / `delivery-threads` | 10s / 4 | 1回の書き込みがこの時間（1秒〜5分）を超えて詰まった購読者を切る / 書き込み用スレッド数（1〜64） |
| `quickvote.cleanup.interval` / `batch-size` / `max-batches-per-run` | 5m / 100 / 50 | 期限切れ削除の周期（10秒〜1日）と件数の区切り |
| `quickvote.idempotency.retention` | 24h | 冪等性記録の保持期間（1分〜7日） |

人数上限や選択肢上限などの「仕様」は設定ではなくコード（`PollRules`）にあります。
範囲外の値（0 や負の値を含む）は、起動時に拒否されます（例: `retention=0s` では、再送が二重に実行されてしまうため）。

### Actuator

| プロファイル | 公開するもの | ポート |
| --- | --- | --- |
| 開発（既定） | `health`（liveness / readiness）と `metrics` | アプリ本体と同じ（8080） |
| `prod` | `health`（liveness / readiness）と `metrics` | **管理ポート 9090 のみ**（本体のポートからは見えない。外部には公開しない） |

`/actuator/health/readiness` は DB に届くかどうかも見ます（DB が止まると 503）。`liveness` は DB を含めません
（DB の障害でアプリを再起動しても直らないため）。
