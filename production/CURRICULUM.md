# 最終設計書（監督決定版）

FableとCodexの設計案（`design/proposal-codex.md`、Fable案は本書に統合済み）を突き合わせ、監督（Claude）が裁定した最終版。
執筆者は `STYLE_GUIDE.md` と本書を必ず読んでから書くこと。

---

## 1. 教材の芯

> **「Javaを覚える教材」ではなく、「サーバーが守るべき約束を決め、実装し、壊して確かめる教材」。**

卒業時の合格文（全章の判断基準）:

> 学習者は、データ・権限・期限・同時実行について守る条件を定義し、Java/Springで実装し、再送や障害を含むテストで確かめ、運用時に異常を発見して復旧する方法を説明できる。AIの生成物も同じ基準でレビューできる。

## 2. 技術の決定

| 項目 | 決定 | 理由 |
| --- | --- | --- |
| Java | 25（LTS）。プレビュー機能は本編で使わない | 安定性と再現性 |
| Spring Boot | 4.1.2（Framework 7 / Security 7 / Jackson 3 / JUnit Jupiter 6 / Jakarta EE 11） | 2026-11時点の安定版。依存はBootの管理に従い、個別にバージョンを書かない |
| Web | Spring MVC（WebFluxは使わない） | DB・トランザクション・スレッドの関係が追いやすい。SSEもMVCで可能 |
| ビルド | **Maven ＋ Maven Wrapper（`./mvnw`）**。Gradleは付録で対訳 | pom.xmlは「データ」で読める。日本の現場での遭遇率。BOMの理解に素直 |
| IDE | **IntelliJ IDEA（無料で使える範囲）推奨**。VS Codeは代替手順を併記 | プロキシ・Bean・ライブラリ内部へのジャンプ、デバッガ。VS Codeキーマップを入れて移行コストを下げる |
| データアクセス | **主線はJdbcClient＋SQL**。JPAは第8部で必修として学ぶ（比較・読解・N+1） | SQLとロックが見える状態で理解してから、ORMの抽象化を評価する |
| DB | PostgreSQL 18（Docker） | |
| マイグレーション | Flyway | |
| テスト | JUnit Jupiter 6、AssertJ、MockMvcTester、Testcontainers | テストは最後にまとめず、各部で段階的に導入 |
| フロント | サンプルアプリに**ビルド不要の静的クライアント**（HTML＋fetch＋EventSource）を同梱 | 学習者はFEのプロ。フロント実装に教材の時間を使わない |
| 仮想スレッド | 第7部（SSE）まではOFF、SSEの章でONにして差を観察 | |

## 3. 通しの題材：即席投票ボード「QuickVote」

会議や飲み会で「今この場で決める」ための、会員登録不要の匿名投票サービス。

- 主催者が投票を作る → 参加者は**8文字の参加コード**（またはそれを埋め込んだQR）で参加する。
- 参加者は選択肢を追加でき（主催者の設定で可否）、1人1票で投票する（再投票は上書き）。
- 得票はリアルタイム（SSE）に伸びる。
- 主催者は「共同主催者の招待リンク」（一度限り）を発行できる。
- 締切時刻を過ぎると投票はAPIで拒否、作成から7日で物理削除。

### なぜこの題材か

会員登録のない小さなWebサービスを一人で作るときに必要になる論点（匿名セッション、共有単位への参加認可、一度限りの招待、短いコードと総当たり対策、人数上限・件数上限と同時実行、冪等性、SSEと再接続、期限、レート制限、ログに本文を残さない）が**すべて自然に必要になる**。

| 論点 | QuickVoteでの具体例 |
| --- | --- |
| 匿名の利用主体 | Cookieの訪問者トークン（DBにはハッシュ） |
| 共有単位への参加認可 | 投票ごとの参加者（HOST / MEMBER） |
| 一度限りの招待 | 共同主催者の招待トークン |
| 短いコード・総当たり | 8文字の参加コード、試行回数制限 |
| 人数上限と同時実行 | 参加者は最大50人 |
| 件数上限と同時実行 | 選択肢は最大10件 |
| 一意性 | 1人1票（UNIQUE＋ON CONFLICT） |
| 冪等性 | 選択肢追加にIdempotency-Key。投票はPUTで本質的に冪等（対比） |
| リアルタイム通知 | 得票・参加人数・選択肢追加をSSE。Last-Event-IDで補完 |
| 期限 | 締切（API拒否）と物理削除（スケジューラ）を分離 |
| 数値 | 得票率をBigDecimalと最大剰余法で合計100%にする |
| 運用 | 選択肢の本文をログに残さない、相関ID、メトリクス |

### QuickVoteの仕様（リファレンス実装の正）

- パッケージ：`com.example.quickvote`（機能ごとのパッケージ：`poll`, `participant`, `vote`, `invite`, `event`, `security`, `common`）
- 上限：参加者50人（主催者含む）、選択肢10件、選択肢ラベル1〜100文字かつUTF-8で400バイト以内、タイトル1〜100文字（**「文字数」はコードポイント数**。グラフェムではない）
- 参加コード：8文字、紛らわしい文字（0/O/1/I/L）を除いた英大文字＋数字（31種）。31^8 ≈ 8.5×10^11
- 訪問者トークン：`SecureRandom`で32バイト→Base64URL。Cookie `QV_VISITOR`（HttpOnly, Secure（本番）, SameSite=Lax, Path=/, Max-Age 30日）。DBにはSHA-256ハッシュのみ保存
- 招待トークン：32バイト乱数、期限24時間、一度限り（`UPDATE ... WHERE used_at IS NULL` で原子的に消費）
- 締切：作成APIは `closesInMinutes`（5〜10080の整数、範囲外はBean Validationで400）を受け取り、**サーバーが`Clock`から締切時刻を計算する**（クライアントの時計や通信時間に左右されない）。物理削除は作成から7日後（`delete_after`）
- 締切後は投票・選択肢追加・招待受諾のいずれも409（`POLL_CLOSED`）
- Actuator：本番（prod）は管理ポート9090（外部非公開）でhealth/metricsを提供。readinessはDB疎通を含む
- 時刻：DBは`timestamptz`、Javaは`Instant`、JSONはUTCのISO 8601。`Clock` Beanを注入してテストで時間を進める

テーブル（Flywayで段階的に追加）:

| テーブル | 主な列 | 導入する章 |
| --- | --- | --- |
| `polls` | id, public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at | 第17章 |
| `options` | id, poll_id, label, created_by, created_at | 第17章 |
| `votes` | poll_id, participant_id, option_id, voted_at（UNIQUE(poll_id, participant_id)） | 第18章 |
| `visitors` | id, token_hash, created_at, last_seen_at | 第32章 |
| `participants` | id, poll_id, visitor_id, role, joined_at, revoked_at（UNIQUE(poll_id, visitor_id)） | 第18章（visitor_idは第32章で導入） |
| `invites` | id, poll_id, token_hash, expires_at, used_at, used_by | 第33章 |
| `idempotency_records` | visitor_id, idem_key, request_hash, status, response_body, created_at | 第27章 |
| `poll_events` | poll_id, seq, type, payload(jsonb), created_at（PK(poll_id, seq)） | 第37章 |

API（`/api`配下、JSON。エラーはProblemDetail）:

| メソッド・パス | 説明 | 権限 |
| --- | --- | --- |
| `POST /api/polls` | 投票を作る（作成者はHOST） | 誰でも（訪問者Cookieを発行） |
| `POST /api/join` `{code}` | 参加コードで参加 | 誰でも（試行回数制限） |
| `GET /api/polls/{pollId}` | 詳細（選択肢・得票・得票率・自分の票） | 参加者 |
| `POST /api/polls/{pollId}/options` | 選択肢追加（`Idempotency-Key`必須） | HOST、または許可時のMEMBER |
| `PUT /api/polls/{pollId}/my-vote` `{optionId}` | 投票（上書き） | 参加者・締切前 |
| `POST /api/polls/{pollId}/close` | 締切を早める | HOST |
| `DELETE /api/polls/{pollId}` | 削除 | HOST |
| `POST /api/polls/{pollId}/invites` | 共同主催者の招待リンク発行 | HOST |
| `POST /api/invites/{token}/accept` | 招待を受ける | 誰でも（一度限り） |
| `GET /api/polls/{pollId}/events` | SSE（`Last-Event-ID`対応） | 参加者 |

**「存在しない投票」と「参加していない投票」はどちらも404**（存在を漏らさない）。

### アプリの育ち方

| 部末 | アプリの状態 |
| --- | --- |
| 第2部 | メモリ上のMapで投票の作成・取得・投票ができる |
| 第3部 | PostgreSQLに保存。Flyway。Testcontainersで実DBテスト。※同時実行の事故はまだ起きる |
| 第4部 | 人数上限・件数上限・1人1票が同時実行でも守られる |
| 第5部 | 再送しても二重登録しない。締切と物理削除 |
| 第6部 | 匿名セッション、権限、招待、CSRF/CORS、レート制限。「他人に触られても壊れない」 |
| 第7部 | 得票がリアルタイムに動く。再接続で補完。2台構成でも届く |
| 第8部 | （JPAは別の実験プロジェクトで比較。本線はJdbcClientのまま） |
| 第9部 | Dockerイメージ化、ログ・メトリクス、公開前チェックリスト |

---

## 4. 章構成（全11部・49章＋付録6）

凡例：【動】解説動画　【図】自作SVG中心　【事故】壊す体験が主役　【読】読解が主役　【設】設計が主役（表と図）

章ファイル：`docs/partNN/chNN-slug.md`。部の扉：`docs/partNN/index.md`。

### 第0部 はじめに（`part00`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 00 | この教材の歩き方 | 教材の芯、到達水準、扱わない範囲（K8s、マイクロサービス、Kafka、WebFlux、ログイン型認証の実装など）、学習時間の目安（約200時間）、AIとの付き合い方3原則（①予測してから聞く ②生成物は動かして観察 ③説明できないコードは採用しない）、各ページの記号の読み方、QuickVoteの紹介（完成形のイメージ） |
| 01 | 開発環境を整える | JDK 25、IntelliJ IDEA（＋VS Code代替）、Docker Desktop、`./mvnw`。「Hello」と「DBにつながった」の2つを確認。Windowsのつまずき表（JAVA_HOME、WSL2、5432競合、文字コード、パスの空白、実行ポリシー） |

### 第1部 TypeScript脳で読むJava（`part01`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 02 | Javaが動くまで：JVM・コンパイル・main 【図】 | `void main()`（コンパクトソースファイル）から始め、`javac`→`.class`→JVM→jar の流れ、クラスパス。Node/TSとの対応表。`public static void main` の呪文はあとで解く |
| 03 | 型の世界：名前で決まる型 | プリミティブと参照、`var`、class・interface・継承・アクセス修飾子、パッケージとimport。例え「TSは『鴨のように歩けば鴨』、Javaは『鴨の免許証がなければ鴨ではない』」（構造的型付け vs 名前的型付け） |
| 04 | データの形：record・enum・sealed・switch | TSの`type`・union・判別可能ユニオンをJavaで書く。網羅漏れがコンパイルエラーになる体験。QuickVoteの投票状態を先取りで定義。`final`/recordでも中身の可変Listは変えられる落とし穴 |
| 05 | nullと例外 | NPEを起こして読む、JSpecifyの`@Nullable`、Optionalは戻り値専用、checked/unchecked例外、try-with-resources、例外を握りつぶさない |
| 06 | コレクション・ジェネリクス・ラムダ・Stream | List/Set/Map、`List.of`の不変、ジェネリクスと型消去（1回だけ）、`map/filter/reduce`との対応、forとStreamの使い分け |
| 07 | 値の落とし穴：equals・数値・時刻・文字 【事故】 | `==`と`equals`（Integerキャッシュ128）、equals/hashCode、`0.1+0.2`→BigDecimal（得票率33.3%×3≠100%を最大剰余法で直す）、`Instant`/`LocalDateTime`/`ZonedDateTime`（JST/UTCずれ事故）、文字数とUTF-8バイト数 |
| 08 | JUnitで最初のテスト | Springなしで得票率計算と状態遷移をテスト。境界値、`@ParameterizedTest`、AssertJ。「AIの生成コードの誤りをテストで示す」 |
| 09 | スレッドと共有状態：サーバーは同時に動く 【動】 | 1リクエスト1スレッドとNodeのイベントループの違い、`counter++`を100スレッドで壊す→`AtomicInteger`、共有フィールドの危険。仮想スレッドの存在だけ紹介 |

### 第2部 Spring Bootの心臓部（`part02`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 10 | Spring Bootとは：最初のAPIと起動の裏側 | Java/Spring Framework/Spring Boot/Tomcat/JVMの層（SVG積層図）、Initializrで生成、pom.xmlとBOM（package.jsonとの違い：lockfileではなくBOMが版を決める）、起動ログを1行ずつ読む、自動構成は「条件付きの設定＝if文」。例え「Bootはひな形ではなく配線済みの電源タップ」 |
| 11 | DIコンテナとBean 【動】 | 手動のコンストラクタ注入→DIコンテナへ。Bean、`@Component/@Service/@Configuration/@Bean`、コンストラクタ注入、singleton、`@Qualifier`、循環依存。React Context/Providerとの対比表（似ている点・違う点）。壊す：`new`して注入されない。BeanにユーザーごとのSTATEを置く事故 |
| 12 | リクエストの旅 【動】 | Tomcatのスレッド→Filter→DispatcherServlet→Controller→Jackson→戻り。Filterを1つ自作して通過ログを見る。以降の章の「地図」 |
| 13 | API契約：DTOとJSON | record DTO、Jackson 3（`tools.jackson`）、HTTPステータス、未知フィールド・欠落・null、Entityをそのまま返さない、日時のJSON表現。「TSの型もJavaの型もJSON境界では守ってくれない」 |
| 14 | 入力検証とエラーの設計 | Bean Validation（`@Valid`）、業務ルール、DB制約の3層。ProblemDetail（RFC 9457）、`@RestControllerAdvice`、400/404/409/422/500のフローチャート、スタックトレースを外に出さない |
| 15 | 設定・プロファイル・秘密 | `application.yaml`、`@ConfigurationProperties`（record＋`@Validated`）、プロファイル、環境変数、起動時に落とす（fail fast）、秘密をGitに入れない |
| 16 | APIをテストする | `@WebMvcTest`＋MockMvcTester、`@MockitoBean`、`@SpringBootTest`との違い、テストの速さ。第2部の到達確認 |

### 第3部 PostgreSQLに正しく保存する（`part03`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 17 | PostgreSQLとFlyway：スキーマをコードで育てる | Docker ComposeでPostgreSQL、BootのDocker Compose連携、FlywayのV1、適用済みファイルを書き換えてチェックサムエラー→「新しいVを足す」。例え「FlywayはDBのGit」 |
| 18 | データモデルと制約設計 【設】 | QuickVoteのER図（Mermaid）、PK（bigint identity＋公開用ID）、FK、UNIQUE、CHECK、NOT NULL、削除方針。「アプリのifは前線、制約は最終防衛線」。単一行で守れる条件と複数行にまたがる条件の区別 |
| 19 | JdbcClientでSQLを書く | Repository、名前付きパラメータ、行→recordのマッピング、更新件数、`DataAccessException`階層、`DuplicateKeyException`→409。SQLインジェクションを実際に通す→バインド変数 |
| 20 | 検索・ページング・インデックス | JOINで行数が増える、集計、NULL、安定ソート、offset/keyset、複合インデックスの列順、`EXPLAIN ANALYZE`を読む |
| 21 | 実PostgreSQLでテストする | Testcontainers、`@ServiceConnection`、Flywayを通したテスト、データ独立性、「H2で通っても安心しない」理由（ON CONFLICT、timestamptz） |

### 第4部 トランザクションと同時実行（`part04`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 22 | トランザクションの境界 | psqlでBEGIN/COMMIT/ROLLBACKを観察→`@Transactional`。2本のINSERTの間で例外：なしだと片方だけ残る、ありだと両方消える。例え「銀行振込」。業務操作単位に境界を置く |
| 23 | @Transactionalの正体：プロキシ 【動】 | プロキシ（例え「秘書」）、自己呼び出し、privateメソッド、checked例外は既定でロールバックしない、例外の握りつぶし、REQUIRED/REQUIRES_NEW、`readOnly`、コネクションとスレッドの紐づけ。ログで実際のBEGIN/COMMITを確認 |
| 24 | スレッドとコネクションプール 【動】 | HikariCP、プールの枯渇、接続待ちタイムアウト、遅いSQL1本で全員が待つ、トランザクション中に外部通信しない。例え「貸出用の傘立て」。仮想スレッドでも接続は増えない |
| 25 | 上限を守る：同時参加の競合とロック 【動】【事故】 | 2つのpsqlで「残り1枠に2人」を手で再現。count→insertでは守れない。`SELECT ... FOR UPDATE`で親行（polls）をロックして再判定。すべての更新経路が同じ規則に従う必要。負荷スクリプトで20本同時→成功は上限まで |
| 26 | 競合の別解と並行性テスト | 楽観ロック（version列）、条件付きUPDATE、UNIQUE＋`ON CONFLICT`（1人1票）、分離レベル（READ COMMITTEDで何が見えるか）、デッドロックとロック順序、有限回の再試行。`CountDownLatch`で狙った競合を作るテスト。「100回動いて失敗しなかった」は根拠にならない |

### 第5部 再送・期限・定期処理（`part05`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 27 | 冪等性：応答が消えても二重登録しない 【動】【事故】 | 保存成功後に応答だけ失う状況を再現。Idempotency-Key、キーのスコープ（訪問者×キー）、同じキーに違う入力→422、同時に同じキー、業務更新と冪等性記録を同じトランザクションに、保持期限。PUTの投票は本質的に冪等（対比）。「exactly once」と言わない。例え「宅配の伝票番号」 |
| 28 | 期限と時刻を設計する | 締切の判定時点（締切直前・同時刻・直後）、`Clock`注入で時間を進めるテスト、状態遷移図（sealedと対応）、「期限切れ拒否」と「物理削除」を分ける理由 |
| 29 | 定期処理：失敗しても続けられるジョブ | `@Scheduled`で期限切れ削除、件数を区切って削除、途中失敗と再実行、2台で同時に走る問題→PostgreSQLのadvisory lock（ShedLockも紹介）、`@Async`とトランザクション |

### 第6部 匿名利用者と認可（`part06`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 30 | 認証・識別・認可を分ける 【設】 | 「誰として扱うか」と「何を許すか」。権限表（非参加者/MEMBER/HOST×操作）。IDを書き換えて他人の投票を覗く攻撃（IDOR）を実演。404で存在を漏らさない。例え「入館証とフロアの入室権」 |
| 31 | Spring Securityの地図 【図】 | 依存を足すと全部401になる理由、`SecurityFilterChain` Bean、Security 7のラムダDSL、標準のFilter群（SVG）。まず全部許可して「何も守られていない」を観察してから閉じる |
| 32 | 匿名セッション：Cookieで利用主体を識別する | 訪問者トークンの発行（SecureRandom）、DBにはハッシュ、Cookie属性、カスタム認証フィルタで`Authentication`に載せる、HttpSessionを使わない理由（再起動・複数台）、Cookieを消したら権限を失う＝仕様として決める。Spring Securityの「匿名認証」とは別物であること |
| 33 | 招待・参加コード・総当たり対策 【事故】 | 一度限りの招待（原子的な消費、GETで消費しない）、参加コードの探索空間の計算、総当たりスクリプトで突破→試行回数制限で止める、定数時間比較、QRは「URLを絵にしただけ」 |
| 34 | ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS 【動】 | ブラウザはCookieを自動で添える、CSRFトークン（SPA向け設定）、SameSite、CORSは「ブラウザのドアマン」でありサーバーを守らない、プリフライト、credentials、HTTPS終端、セキュリティヘッダー |
| 35 | レート制限と資源の保護 | 主体・IP・対象の複数軸で制限、429とRetry-After、リクエストサイズ上限、自作トークンバケットの仕組み→複数台では共有が必要、ライブラリ（Bucket4j）の紹介 |

### 第7部 リアルタイム通知（`part07`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 36 | SSE入門 | ポーリング＝何度もノック、SSE＝ラジオ、WebSocket＝電話。`SseEmitter`、イベント形式（id/event/data/retry）、ハートビート、タイムアウト、切断時の後始末、認可済みの接続。仮想スレッドをONにして違いを観察 |
| 37 | 取りこぼしを防ぐ：イベント履歴とLast-Event-ID 【動】 | 業務更新とイベント履歴を同じトランザクションで保存、投票ごとのロック下で連番採番（シーケンスの順≠commit順の罠）、再接続で`Last-Event-ID`以降を再送、重複排除、履歴切れなら全量再取得 |
| 38 | 2台構成で起きること 【事故】 | app×2＋nginxで「隣のサーバーにつないだ人に届かない」を再現→PostgreSQLのLISTEN/NOTIFYで橋渡し（Redis Pub/Subは概説）。セッション・レート制限・スケジューラ・接続数も2台で見直す |

### 第8部 JPAを理解して使い分ける（`part08`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 39 | JPAは何を肩代わりするのか | Entity、Repository、永続コンテキスト（例え「レジのカゴ」）、dirty checking、flush、Entityの状態遷移。`save()`を呼ばなくてもUPDATEが出る。同じテーブルをJPAで書き、JdbcClient版と発行SQLを並べる |
| 40 | N+1と取得戦略 【動】【事故】 | 一覧でSQLが101本流れるのをログで数える、Lazy/Eager、LazyInitializationException、fetch join／EntityGraph／DTO射影、open-in-view（OSIV）、`@Version` |
| 41 | SQLとJPAの使い分け、AI生成JPAコードのレビュー 【読】 | 方式の選び方、混在時の注意（flush、古い状態）、MyBatisのコラム（日本の現場でよく見る）。罠を5つ仕込んだJPAコードのレビュー演習 |

### 第9部 運用：公開後に責任を持つ（`part09`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 42 | Dockerでパッケージング | マルチステージビルド、layered jar（例え「地層」）、非root実行、JVMメモリとコンテナ制限、Compose（app＋db）、ヘルスチェック |
| 43 | ログ：あとから原因を追えるように | 構造化ログ（Boot標準のJSON出力）、相関ID（MDC）、**本文・トークン・参加コードを出さない**（許可した項目だけ出す）、例外は1回だけ出す、ログレベル運用 |
| 44 | メトリクスとヘルスチェック | Actuator、Micrometer、HikariとHTTPの指標、カーディナリティ、readiness/liveness、最低限のアラート |
| 45 | 性能と堅牢性：タイムアウト・負荷・停止 | p95/p99、プールサイズ、タイムアウトを「全部」決める、graceful shutdown、負荷試験（k6）、スレッドダンプ |
| 46 | リリースと復旧 | GitHub ActionsでCI、expand/contractマイグレーション（稼働中に列を消さない）、バックアップと復元、依存の更新と脆弱性、公開前チェックリスト |

### 第10部 卒業（`part10`）

| 章 | タイトル | ゴール・見せ方 |
| --- | --- | --- |
| 47 | AIと組むレビュー術 【読】 | AI生成コードのレビュー観点チェックリスト（約30項目）、古いSpringの書き方の見抜き方と直し方、欠陥入りコード3本のレビュー演習 |
| 48 | 卒業制作：自分のサービスを設計する 【設】 | 要件→データモデル→API→権限表→異常系→同時実行→期限→運用→検証計画のテンプレートで、自分の題材を一人で設計する。卒業チェックリスト（can-do 30項目） |

### 付録（`appendix`）

| ファイル | 内容 |
| --- | --- |
| `appendix/gradle.md` | Gradle（build.gradle.kts）対訳 |
| `appendix/login-auth.md` | ログイン型認証の概観（パスワード＋BCrypt、セッションとJWT、OAuth2/OIDC）。作らないが読めるように |
| `appendix/boot3-to-4.md` | Spring Boot 3→4 の差分カード（古い記事・AIの出力を直すため） |
| `appendix/glossary.md` | 用語集 |
| `appendix/troubleshooting.md` | トラブルシューティング（Windows/Docker/IntelliJ/Maven） |
| `appendix/ts-java.md` | TypeScript ↔ Java 対応表 |

---

## 5. 章の型（固定しない）

すべての章で揃えるのは「見出しの順番」ではなく、次の4点。

1. **判断する対象**：何を決める章か
2. **観測する対象**：HTTP、SQL、DBの状態、ログなど
3. **守る条件**：成功・失敗を何で判定するか
4. **学習者の成果**：コード・テスト・図・設計理由のどれか

章タイプは5種類。入り方を変える。

| タイプ | 入り方 |
| --- | --- |
| 概念章 | 問い → 例え → 図/動画 → 最小コード |
| 構築章 | 完成形を見せる → 手順 → 動かす → 小さく壊す |
| 事故章 | いきなり事故を再現 → 予測 → 原因 → 対策 → 固定 |
| 読解章 | 既存コードやAI生成コードを読むのが主役。書かない |
| 設計章 | 表と図が主役。コードは最後 |

使える部品（必要なものだけ選ぶ）:

- **予測カード**：実行前に結果を選ばせる（Predict-Observe-Explain）
- **この時点の保証**：未完成のコードは「まだ〜は守れていない」と明示（公開してよいコードと誤解させない）
- **3つの脳**：段落の視点を示すバッジ「🖥 フロント脳」「⚙️ サーバー脳」「🗄 DB脳」
- **「今は分からなくていい」**：前方参照に付け、解く章にリンク
- **まとめ3点セット**：①一言で言うと（例えを使わず技術用語で）②現場でこう聞かれる ③自分のサービスならどこに当たるか

---

## 6. 説明手段の配分

### 動画（Blender＋VOICEVOX、各45〜90秒）

コードは映さない（陳腐化防止）。概念と動きだけ。本文側で「動画の○○はコードではここ」と対応づける。静止画の代替説明と字幕全文も本文に置く。

| # | ID | タイトル | 章 |
| --- | --- | --- | --- |
| V1 | `thread-model` | 店員モデル：Nodeとjavaの同時処理 | 09 |
| V2 | `di-container` | DIコンテナ：配役表で役者をつなぐ | 11 |
| V3 | `request-journey` | リクエストの旅 | 12 |
| V4 | `tx-proxy` | @Transactionalと秘書（プロキシ） | 23 |
| V5 | `connection-pool` | コネクションプール：傘立ての傘が足りない | 24 |
| V6 | `last-seat-race` | 残り1枠に2人：競合とロック | 25 |
| V7 | `idempotency` | 応答が消えても二重登録しない | 27 |
| V8 | `csrf-cookie` | ブラウザはCookieを自動で添える | 34 |
| V9 | `sse-resume` | SSEの切断とLast-Event-ID | 37 |
| V10 | `n-plus-one` | N+1：SQLの滝 | 40 |

### 中心の例え（シリーズで統一）

| 概念 | 例え | 外れる点（本文で必ず添える） |
| --- | --- | --- |
| Webアプリの層 | レストラン（ホール＝Controller、厨房＝Service、食材庫＝Repository/DB、注文票＝リクエスト） | 実際は1人の店員（スレッド）が注文を最後まで運ぶ |
| スレッド | 店員（客ごとに店員が付く）。Nodeは超高速な店員1人 | |
| DIコンテナ | 劇団の演出家が配役表を作り、役者に相手役を手渡す | 起動時に一括、基本1人1役（singleton） |
| トランザクション | 銀行振込 | |
| プロキシ | 本人の前に立つ秘書 | |
| コネクションプール | 貸出用の傘立て | |
| 冪等性キー | 宅配の伝票番号 | |
| SSE | ラジオ（片方向・つけっぱなし） | |
| 永続コンテキスト | レジのカゴ | |
| Flyway | DBのGit | 書き換え・巻き戻しが基本できない |
| 認証と認可 | 入館証とフロアの入室権 | |
| 制約 | 最終防衛線 | |

### 挿絵（Codexで生成）

- 各部の扉ページに1枚（16:9、フラットなベクター調、文字なし）。
- 中心の例え話のイラスト（レストラン、劇団、傘立て、伝票番号など）。
- 技術図（正確なラベルや矢印が学習内容そのもの）は生成画像を使わず、SVG/Mermaidで作る。

---

## 7. 演習と到達確認

- 章ごと：確認（予測の答え合わせ・観察事実）／応用（QuickVoteに小さな変更）／説明（「現場でこう聞かれる」に200字で答える）。解答は折りたたみ。
- 部末：「この部のチェックリスト」（can-do文、10〜15項目）＋統合課題（事故対応ドリル、Red→Green課題、設計説明のいずれか）。
- 到達の4段階：①再現できる ②説明できる ③検証できる ④変更できる。部末は③、卒業は主要領域で④。

## 8. コード資産

| 場所 | 内容 |
| --- | --- |
| `code/java-basics/` | 第1部の実験。`java ファイル名.java` で直接実行できる単一ファイル |
| `code/quickvote/` | QuickVoteのリファレンス実装（完成形）。Maven Wrapper、テスト一式 |
| `code/jpa-lab/` | 第8部のJPA比較実験プロジェクト |

本文のコードは、リファレンス実装と矛盾してはならない。途中段階のコードを本文に載せるときは、最終形との差分を意識し「この時点の保証」を添える。
