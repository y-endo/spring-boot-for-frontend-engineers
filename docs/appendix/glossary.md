# 用語集

<div class="goal-box" markdown>

この付録の使い方

- 本編で出てきた用語を、**頭文字ごと**に引ける（かなは五十音順、アルファベットで始まる用語は「英字」節にまとめた）
- 各用語に、**短い定義**、**初出の章へのリンク**、**関連用語**を付けた
- 迷ったら、ページ内検索（`Ctrl+F` / `⌘F`）か、画面上部のサイト内検索（`/` キー）に用語を入れる

</div>

## 引き方

**頭文字へジャンプ**：[ア行](#a) ・ [カ行](#ka) ・ [サ行](#sa) ・ [タ行](#ta) ・ [ハ行](#ha) ・ [マ行](#ma) ・ [ラ行](#ra) ・ [英字](#alpha)

- 見出しの用語の下に、定義（2〜3文）と、**初出**（その用語を最初に説明した章）、**関連**を置いています。
- 章の中の `!!! note "用語：○○"` の枠を機械的に集め、複数の章で説明されている用語（コネクションプール、トランザクション、不変条件）は1つにまとめました。複数の章にまたがる用語は「**詳しくは**」で、深く扱う章を示します。
- 章に用語の枠はないものの、本文で説明している用語（CSRF、CORS、SSE、デッドロックなど）は **補足** として加えています。補足の用語は、「初出」ではなく「**説明のある章**」と書いています。
- 定義は各章の説明に合わせています。くわしい実験や図は、リンク先の章を読んでください。
- トラブルが起きたときは、[トラブルシューティング](troubleshooting.md)から症状で引けます。

---

## ア行 { #a }

### 値渡し（pass by value） { #t-pass-by-value }

メソッドを呼ぶとき、引数の**値のコピー**が渡されます。プリミティブ型なら数値のコピー、参照型なら**矢印（参照）のコピー**です。Java に「参照渡し」はなく、「参照の値渡し」です。JavaScript のオブジェクトの渡し方とほぼ同じです。

**初出**：[第3章 型の世界：名前で決まる型](../part01/ch03-types.md)

### アドバイザリロック（advisory lock） { #t-advisory-lock }

PostgreSQL が提供する、**アプリが好きな数値を鍵にして取れるロック**です。テーブルの行や表とは関係なく、DB は鍵の意味を知りません（advisory＝助言的）。`pg_try_advisory_lock(鍵)` は、取れたら `true`、取れなければ待たずに `false` を返します。

**初出**：[第29章 定期処理：失敗しても続けられるジョブ](../part05/ch29-scheduling.md)

**関連**：[SELECT ... FOR UPDATE](#t-for-update)、[@Scheduled](#t-scheduled)

### アノテーション { #t-annotation }

`@Override` のように、コードに付ける注釈の仕組みです。コンパイラやフレームワークが読み取って、動作を変えます。TypeScript のデコレータに近いものですが、Java のアノテーションは「それ自体は何もしない目印」で、読み取る側が処理します。

**初出**：[第3章 型の世界：名前で決まる型](../part01/ch03-types.md)

**関連**：[@RestController](#t-restcontroller)、[@Scheduled](#t-scheduled)、[@Async](#t-async)

### インデックス（索引） { #t-index }

列の値を**並べ替えた状態で別に持っておく**仕組みです（PostgreSQL の標準は B-tree という木構造）。本の巻末の索引と同じで、全ページをめくらずに目的の位置に飛べます。代わりに、行を追加・更新するたびに索引も更新する必要があります。

**初出**：[第20章 検索・ページング・インデックス](../part03/ch20-query-index.md)

**関連**：[ON CONFLICT](#t-on-conflict)

### 永続コンテキスト（Persistence Context） { #t-persistence-context }

トランザクションの間、**Entity を管理しておく作業領域**です。DB から読んだ Entity はここ（`EntityManager` の中）に入り、同じ id の Entity は1つだけ保持されます。**入ったときの値を覚えておいて**、あとで変更があったかを調べます。Hibernate では `Session` と呼ばれるものがこれに当たります。

**初出**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[Entity](#t-entity)、[ダーティチェック](#t-dirty-checking)、[flush](#t-flush)、[open-in-view](#t-osiv)

### 親行ロック { #t-parent-row-lock }

人数や件数の上限を守るために、**数える前に親の行（QuickVote では `polls` の1行）を `SELECT ... FOR UPDATE` でロックする**方法です。鍵を取る前に数えた値は使い回さず、「ロック → 再カウント → 判定 → 書き込み」の順で行います。

**説明のある章**：[第25章 上限を守る：同時参加の競合とロック](../part04/ch25-locking.md)　**詳しくは**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[SELECT ... FOR UPDATE](#t-for-update)、[デッドロック](#t-deadlock)、[悲観ロック](#t-pessimistic-lock)

### オリジン（origin）とサイト（site） { #t-origin-site }

**オリジン**は「スキーム＋ホスト＋**ポート**」の組、**サイト**は「スキーム＋登録可能ドメイン」で**ポートを区別しません**。CORS（読めるか）はオリジンで、`SameSite`（Cookie を付けるか）はサイトで判定されます。

**初出**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[CORS](#t-cors)、[CSRF](#t-csrf)、[Cookie の属性](#t-cookie-attr)、[プリフライト](#t-preflight)

## カ行 { #ka }

### カーディナリティ { #t-cardinality }

メトリクスのタグの**値の種類の数**です。`status` は数種類ですが、`userId` は利用者の数だけあります。高いと時系列の本数が爆発し、メモリ・保存コスト・検索の遅さが跳ね上がるので、ID や URL の実物はタグに入れません。

**初出**：[第44章 メトリクスとヘルスチェック](../part09/ch44-metrics.md)

**関連**：[Actuator／Micrometer](#t-actuator)

### 仮想スレッド（Virtual Thread） { #t-virtual-thread }

JVM が管理する**とても軽いスレッド**で、数万〜数百万個作れます。待ち（ブロッキング）に入ると背後の OS スレッドを自動で手放すので、Node の `await` に近い効率を同期のコードのまま得られます。通常のスレッドは**プラットフォームスレッド**と呼びます。DB 接続の数は増えません。

**初出**：[第9章 スレッドと共有状態：サーバーは同時に動く](../part01/ch09-threads.md)　**詳しくは**：[第24章 スレッドとコネクションプール](../part04/ch24-connection-pool.md)、[第36章 SSE入門：サーバーから流し続ける](../part07/ch36-sse.md)

**関連**：[コネクションプール](#t-connection-pool)、[競合状態](#t-race-condition)

### 型消去（type erasure） { #t-type-erasure }

Java のジェネリクスは**コンパイルのときだけ**働きます。実行時の `.class` からは `<String>` のような型引数が**消えています**。ジェネリクスが導入される前に作られたコードとの互換性を保つための設計です。

**初出**：[第6章 コレクション・ジェネリクス・ラムダ・Stream](../part01/ch06-collections-streams.md)

### 競合状態（レースコンディション） { #t-race-condition }

複数のスレッドが共有データを同時に読み書きして、**実行のタイミングによって結果が変わる**状態です。再現性がなく、テストで見つけにくいのが厄介です。「たまたま動く」は「正しい」ではありません。

**初出**：[第9章 スレッドと共有状態：サーバーは同時に動く](../part01/ch09-threads.md)　**詳しくは**：[第25章 上限を守る：同時参加の競合とロック](../part04/ch25-locking.md)

**関連**：[親行ロック](#t-parent-row-lock)、[ThreadLocal](#t-thread-local)、[仮想スレッド](#t-virtual-thread)

### 構造化ログ／ECS { #t-structured-log }

**構造化ログ**は、1行の文章ではなく**項目名のついたデータ**（ふつうは JSON）で出すログです。**ECS**（Elastic Common Schema）は `@timestamp` や `log.level` のような**項目名の標準**で、ログ収集ツールがそのまま読めます。

**初出**：[第43章 ログ：あとから原因を追えるように](../part09/ch43-logging.md)

**関連**：[MDC](#t-mdc)、[相関ID](#t-correlation-id)

### コードポイントと書記素（グラフェム） { #t-codepoint }

**コードポイント**は Unicode が文字に振った番号です。絵文字や「𠮷」は UTF-16 では2つの単位に分かれます。**書記素（グラフェム・クラスター）**は、複数のコードポイントが組み合わさって1つの見た目になるまとまり（国旗、家族の絵文字、濁点つきの文字など）です。

**初出**：[第7章 値の落とし穴：equals・数値・時刻・文字](../part01/ch07-value-pitfalls.md)

### コネクションプール（connection pool） { #t-connection-pool }

DB への接続は作るのに時間がかかるので、**あらかじめ数本つないでおいて使い回す**仕組みです。Spring Boot の既定は HikariCP です。借りられる接続がなければ**返却を待たされ**、同時に貸し出せる本数に上限があるため、枯渇が事故の原因になります。

**初出**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)　**詳しくは**：[第24章 スレッドとコネクションプール](../part04/ch24-connection-pool.md)

**関連**：[トランザクション](#t-transaction)、[仮想スレッド](#t-virtual-thread)、[open-in-view](#t-osiv)

### コンテナ／イメージ { #t-container }

**コンテナ**は、アプリとその依存を他と隔離して動かす仕組みです。仮想マシンより軽く、OS のカーネルを共有するので起動が速く、コマンド1つで起動・削除できます。**イメージ**はアプリと実行環境を固めた読み取り専用の雛形で、コンテナはイメージから起動した実行中のプロセスです。

**初出**：[第1章 開発環境を整える](../part00/ch01-setup.md)　**詳しくは**：[第42章 Dockerでパッケージング](../part09/ch42-docker.md)

**関連**：[Docker Compose](#t-docker-compose)、[マルチステージビルド](#t-multistage)

### コンパクトソースファイル { #t-compact-source }

クラス宣言なしで `void main()` だけを書いた Java ファイルです。学習や小さなツール向けの書き方で、Java 25 で正式機能になりました。Spring などの実務のコードは通常のクラスで書きます。

**初出**：[第1章 開発環境を整える](../part00/ch01-setup.md)

## サ行 { #sa }

### 最大剰余法（ハミルトン方式） { #t-max-remainder }

「合計が決まった数になるように、整数を割り振る」方法です。①まず全員を**切り捨て**で割り当てる。②足りない分（**余り**）を、切り捨てた端数が**大きい順に**1ずつ配る。得票率を足して必ず100にしたいときに使います。

**初出**：[第7章 値の落とし穴：equals・数値・時刻・文字](../part01/ch07-value-pitfalls.md)

### 識別・認証・認可 { #t-id-authn-authz }

**識別**は「あなたはこの人として扱います」という名札付け、**認証**はその名札が本物かの確認、**認可**は「この人にこの操作を許すか」の判断です。Spring Security では識別と認証をまとめて「認証（Authentication）」と呼ぶことが多く、この教材の分け方より粗いです。

**初出**：[第30章 認証・識別・認可を分ける](../part06/ch30-authz-design.md)

**関連**：[IDOR](#t-idor)

### 自己呼び出し { #t-self-invocation }

同じクラスの中から、`this` 経由で `@Transactional` 付きのメソッドを呼ぶことです。**プロキシ（秘書）を通らない**ので、トランザクションが始まりません。コンパイルエラーも例外も出ず、ひっそり効かないのが怖い点です。`@Async` も同じ理由で効きません。

**説明のある章**：[第23章 @Transactionalの正体：プロキシ](../part04/ch23-transactional-proxy.md)　**詳しくは**：[第29章 定期処理：失敗しても続けられるジョブ](../part05/ch29-scheduling.md)

**関連**：[プロキシ](#t-proxy)、[トランザクション](#t-transaction)、[@Async](#t-async)

### 自動構成（auto-configuration） { #t-autoconfig }

Spring Boot が、クラスパス（入っているライブラリ）と設定を見て、必要な設定を自動で足す仕組みです。実体は「条件付きの設定」で、あなたが先に同じ型の Bean を定義すると引っ込みます（`@ConditionalOnMissingBean`）。ソースに現れないので、`--debug` のレポートで調べます。

**説明のある章**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[Spring Framework と Spring Boot](#t-spring-boot)、[BOM](#t-bom)、[Bean](#t-bean)

### スレッドダンプ { #t-thread-dump }

ある瞬間の、**全スレッドが何をしているか（どのメソッドの途中か）**の一覧です。「遅い・止まっている」ときに、スレッドが何を待っているかが分かります。

**初出**：[第45章 性能と堅牢性：タイムアウト・負荷・停止](../part09/ch45-performance.md)

### 制約の名前 { #t-constraint-name }

`CONSTRAINT participants_poll_visitor_key` のように、制約には名前を付けます。付けないと自動で決まり、エラーメッセージや後からの変更（`ALTER TABLE ... DROP CONSTRAINT`）で扱いにくくなります。

**初出**：[第18章 データモデルと制約設計](../part03/ch18-data-modeling.md)

### 相関ID（requestId） { #t-correlation-id }

各リクエストに付ける識別子です。ログと応答に載せることで、**1つのリクエストに関するログを後から追えます**。QuickVote では `RequestIdFilter` が付け、ログには MDC を通じて添えられます。

**説明のある章**：[第12章 リクエストの旅](../part02/ch12-request-journey.md)　**詳しくは**：[第43章 ログ：あとから原因を追えるように](../part09/ch43-logging.md)

**関連**：[MDC](#t-mdc)、[Filter](#t-filter)、[構造化ログ／ECS](#t-structured-log)

## タ行 { #ta }

### ダーティチェック（dirty checking） { #t-dirty-checking }

永続コンテキストが、読み込んだ時点の値の控えと現在の値を比べ、**変わった Entity だけ `UPDATE` を発行する**仕組みです。`@Transactional` の中で Entity を書き換えると、`save()` を呼ばなくても更新されるのはこのためです。

**説明のある章**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[永続コンテキスト](#t-persistence-context)、[flush](#t-flush)、[Entity](#t-entity)

### checked 例外／unchecked 例外 { #t-checked }

**checked 例外**は `Exception` のうち `RuntimeException` でないもの（`IOException` など）で、`catch` するか `throws` を書かないとコンパイルできません。**unchecked 例外**は `RuntimeException` の仲間で、書かなくても投げられます。`@Transactional` は、既定では checked 例外でコミットし、unchecked でロールバックします。

**説明のある章**：[第5章 nullと例外](../part01/ch05-null-exceptions.md)　**詳しくは**：[第23章 @Transactionalの正体：プロキシ](../part04/ch23-transactional-proxy.md)

**関連**：[トランザクション](#t-transaction)、[プロキシ](#t-proxy)

### 遅延ロード { #t-lazy-loading }

JPA で、親を読んだときには関連（子の一覧など）を読まず、`poll.getOptions()` のように**呼んだ時点で初めて SQL を出す**動きです。永続コンテキストが閉じたあとで呼ぶと `LazyInitializationException` になります。ループの中で発動すると N+1 問題の原因です。

**説明のある章**：[第40章 N+1と取得戦略](../part08/ch40-n-plus-one.md)

**関連**：[N+1問題](#t-n-plus-one)、[open-in-view](#t-osiv)、[永続コンテキスト](#t-persistence-context)

### デッドロック { #t-deadlock }

2つのトランザクションが、互いに相手が持つ鍵を待ち合って**永遠に動けなくなる**状態です。PostgreSQL は `deadlock_timeout`（既定1秒）後に検出し、片方を強制的に失敗させます（`deadlock detected`）。複数の鍵を取る処理は、**取る順序を固定**して防ぎます。

**説明のある章**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[SELECT ... FOR UPDATE](#t-for-update)、[親行ロック](#t-parent-row-lock)

### トークンバケット { #t-token-bucket }

回数制限の方式の1つで、訪問者ごとに「バケツ（トークンの入れ物）」を持ち、1回の試行で1個使い、一定間隔で1個補充します。QuickVote の例では容量10、6秒に1個補充です。使い切ると 429 と `Retry-After`（待つべき秒数）を返します。

**説明のある章**：[第33章 招待・参加コード・総当たり対策](../part06/ch33-invites-brute-force.md)　**詳しくは**：[第35章 レート制限と資源の保護](../part06/ch35-rate-limiting.md)

### トランザクション { #t-transaction }

複数の操作を「**全部成功か、全部なかったことにするか**」の1つのまとまりにする仕組みです。`COMMIT` で全部を確定し、`ROLLBACK`（または失敗）で全部をなかったことにします。途中の状態は、他のトランザクションに見えません（既定の設定では。分離レベルは第26章）。

**初出**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)　**詳しくは**：[第22章 トランザクションの境界](../part04/ch22-transactions.md)、[第23章 @Transactionalの正体：プロキシ](../part04/ch23-transactional-proxy.md)

**関連**：[ACID](#t-acid)、[分離レベル](#t-isolation)、[プロキシ](#t-proxy)、[コネクションプール](#t-connection-pool)

## ハ行 { #ha }

### ハートビート { #t-heartbeat }

つなぎっぱなしの SSE で、イベントがない間も**定期的に流す小さなデータ**です。QuickVote は `:heartbeat` というコメント行を既定15秒ごとに送ります。無通信で接続が切られるのを防ぎ、書き込みの失敗で切断を知る手がかりにもなります。nginx の `proxy_read_timeout` より短くします。

**説明のある章**：[第36章 SSE入門：サーバーから流し続ける](../part07/ch36-sse.md)　**詳しくは**：[第38章 2台構成で起きること](../part07/ch38-multi-instance.md)

**関連**：[SSE](#t-sse)、[Last-Event-ID](#t-last-event-id)

### バイトコード { #t-bytecode }

JVM が理解する、機械語に近い命令の列で、`.class` ファイルの中身です。CPU が直接実行する機械語ではなく、JVM が（必要に応じて機械語に変換しながら）実行します。

**初出**：[第2章 Javaが動くまで：JVM・コンパイル・main](../part01/ch02-how-java-runs.md)

**関連**：[JVM](#t-jvm)、[JIT](#t-jit)、[jar](#t-jar)

### パーセンタイル（p95、p99） { #t-percentile }

リクエストを遅い順に並べたとき、**下から95%（99%）の位置**の値です。p95 = 300 ms は「95%のリクエストは300 ms以内に終わる。残り5%はそれより遅い」という意味です。平均には現れない「一部の遅さ」を見るために使います。

**初出**：[第45章 性能と堅牢性：タイムアウト・負荷・停止](../part09/ch45-performance.md)

**関連**：[負荷試験／k6](#t-load-test)、[Actuator／Micrometer](#t-actuator)

### パターンマッチング { #t-pattern-matching }

値の型や形を調べて、同時に変数に取り出す仕組みです。`case Open o ->` は「`state` が `Open` なら、それを `o` として取り出す」の意味です。TypeScript の絞り込み（`if (s.kind === "open")` のあとで `s.closesAt` が使えること）と同じ目的です。

**初出**：[第4章 データの形：record・enum・sealed・switch](../part01/ch04-data-shapes.md)

**関連**：[sealed](#t-sealed)

### パッケージ { #t-package }

クラスをグループ化する名前空間です。ドット区切りの名前（`com.example.quickvote.poll`）で表し、**フォルダ構造と一致**させます。TypeScript のモジュールに近い役割ですが、Java は**ファイルパスではなく、クラスの完全な名前**で import します。

**初出**：[第3章 型の世界：名前で決まる型](../part01/ch03-types.md)　**詳しくは**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[アノテーション](#t-annotation)

### 悲観ロック { #t-pessimistic-lock }

読むときに `SELECT ... FOR UPDATE` で行を押さえ、他のトランザクションを**待たせる**方式です。対して、押さえずに書くときに衝突を検出するのが楽観ロックです。QuickVote の人数上限は、この方式（親行ロック）で守っています。

**説明のある章**：[第25章 上限を守る：同時参加の競合とロック](../part04/ch25-locking.md)　**詳しくは**：[第40章 N+1と取得戦略](../part08/ch40-n-plus-one.md)

**関連**：[SELECT ... FOR UPDATE](#t-for-update)、[楽観ロック](#t-optimistic-lock)、[親行ロック](#t-parent-row-lock)

### 負荷試験／k6 { #t-load-test }

**負荷試験**は、本番に近い形のリクエストをたくさん送り、応答時間とエラーを測る試験です。**k6**は JavaScript で書くシナリオを実行できる負荷試験ツールで、Docker で動かせます（`docker run grafana/k6`）。

**初出**：[第45章 性能と堅牢性：タイムアウト・負荷・停止](../part09/ch45-performance.md)

**関連**：[パーセンタイル](#t-percentile)

### 不変条件（守るべき約束） { #t-invariant }

**読み**：ふへんじょうけん

処理の前後で、**いつも成り立っていなければならない条件**です。この教材では「守るべき約束」と呼びます。「参加者は最大50人」のように、**数えられる文**で書きます。「安全に」「正しく」は約束ではありません。

**初出**：[第0章 この教材の歩き方](../part00/ch00-how-to-learn.md)　**詳しくは**：[第48章 卒業制作：自分のサービスを設計する](../part10/ch48-capstone.md)

**関連**：[競合状態](#t-race-condition)

### 分離レベル（isolation level） { #t-isolation }

同時に動くトランザクション同士が、**互いの変更をどこまで見られるか**を決める設定です。PostgreSQL の既定は **READ COMMITTED** です。レベルを上げるほど見える世界が安定し、競合の失敗（やり直しが必要）が増えます。

**初出**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[トランザクション](#t-transaction)、[write skew](#t-write-skew)

### プリフライト { #t-preflight }

**単純でない**別オリジンの要求（JSON の `Content-Type`、カスタムヘッダー、`PUT`/`DELETE` など）の前に、ブラウザが自動で送る `OPTIONS` リクエストです。サーバーの `Access-Control-Allow-*` ヘッダーで許可されたときだけ、本体を送ります。単純な要求（`GET`、フォームの `POST` など）にはありません。

**初出**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[CORS](#t-cors)、[オリジン](#t-origin-site)

### プロキシ（proxy） { #t-proxy }

「代理」の意味で、本物のオブジェクトと**同じ型に見える**別のオブジェクトです。呼び出しを受けると、本物を呼ぶ**前後に**追加の処理（ここではトランザクションの開始・終了）を挟んで取り次ぎます。Spring の方式の1つが CGLIB で、本物のクラスの**サブクラス**を実行時に生成します（名前の `$$SpringCGLIB$$` が印）。もう1つの JDK 動的プロキシは `interface` から作ります。Spring Boot の既定は CGLIB です。

**初出**：[第23章 @Transactionalの正体：プロキシ](../part04/ch23-transactional-proxy.md)　**詳しくは**：[第29章 定期処理：失敗しても続けられるジョブ](../part05/ch29-scheduling.md)

**関連**：[自己呼び出し](#t-self-invocation)、[トランザクション](#t-transaction)、[@Async](#t-async)

### プロファイル（profile） { #t-profile }

アプリの**動作モードの名前**です（`dev`、`test`、`prod` など）。有効にすると、①`application-{名前}.yaml` が上に重なり、②`@Profile("名前")` を付けた Bean が有効になります。

**初出**：[第15章 設定・プロファイル・秘密](../part02/ch15-configuration.md)

**関連**：[fail fast](#t-fail-fast)

### 冪等性（べきとうせい、idempotency） { #t-idempotency }

**同じ操作を何回繰り返しても、結果が1回やったときと同じ**になる性質です。HTTP の仕様では `GET`・`PUT`・`DELETE` は冪等、`POST` は冪等とは限りません。「結果」はサーバーに残る状態や効果のことで、応答のステータスコードまで同じとは限りません（`DELETE` の2回目が 404 になるように）。

**初出**：[第27章 冪等性：応答が消えても二重登録しない](../part05/ch27-idempotency.md)

**関連**：[Idempotency-Key](#t-idempotency-key)

## マ行 { #ma }

### マイグレーション（migration） { #t-migration }

DB のスキーマ（テーブル・列・制約などの形）を、ある状態から次の状態へ変える**1回分の変更**です。Flyway では、1つのマイグレーション＝1つの SQL ファイルです。

**初出**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)

**関連**：[Flyway](#t-flyway)、[checksum](#t-checksum)、[expand／contract](#t-expand-contract)

### マルチステージビルド { #t-multistage }

`Dockerfile` に `FROM` を複数書き、前のステージの成果物だけを後ろのステージへ `COPY --from=` で渡す方法です。コンパイラ、ソースコード、Maven のキャッシュを**最終イメージに持ち込まない**ために使います。

**初出**：[第42章 Dockerでパッケージング](../part09/ch42-docker.md)

**関連**：[コンテナ／イメージ](#t-container)

### メソッド参照 { #t-method-ref }

`String::length` のように、既存のメソッドをラムダの代わりに名前で渡す書き方です。`s -> s.length()` と同じ意味になります。`::` の左がクラス（または変数）、右がメソッド名です。

**初出**：[第6章 コレクション・ジェネリクス・ラムダ・Stream](../part01/ch06-collections-streams.md)

## ラ行 { #ra }

### 楽観ロック（optimistic locking） { #t-optimistic-lock }

「めったに衝突しない」と**楽観**して、読んでいる間はロックを取らず、**書くときに「自分が読んだあとで変わっていないか」を確認する**方式です。変わっていたら失敗させます。JPA では `@Version` の列を `UPDATE ... WHERE version=?` で比べ、0件更新なら `ObjectOptimisticLockingFailureException` になります。

**初出**：[第40章 N+1と取得戦略](../part08/ch40-n-plus-one.md)　**詳しくは**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[悲観ロック](#t-pessimistic-lock)、[Entity](#t-entity)

## 英字 { #alpha }

### ACID（原子性など） { #t-acid }

**読み**：アシッド

トランザクションの性質の頭文字です。**A**tomicity（原子性：全部成功か、全部なかったことになる）、**C**onsistency（一貫性：制約を破る状態にならない）、**I**solation（独立性：同時に動く別のトランザクションから途中の状態がどこまで見えるか）、**D**urability（永続性：コミットしたら落ちても消えない）。この教材で確実にしたいのは A で、I は「思っているより弱い」ことを第25章の事故で体験します。

**説明のある章**：[第22章 トランザクションの境界](../part04/ch22-transactions.md)

**関連**：[トランザクション](#t-transaction)、[分離レベル](#t-isolation)

### Actuator／Micrometer { #t-actuator }

**読み**：アクチュエーター／マイクロメーター

**Actuator** は Spring Boot が提供する**運用向けの HTTP エンドポイント**の集まりです（`/actuator/health`、`/actuator/metrics` など）。**Micrometer** は指標を計測するための共通の窓口で、ログの SLF4J のように、裏側（Prometheus、Datadog など）を差し替えられます。

**初出**：[第44章 メトリクスとヘルスチェック](../part09/ch44-metrics.md)

**関連**：[liveness／readiness](#t-liveness)、[カーディナリティ](#t-cardinality)

### AssertJ { #t-assertj }

**読み**：アサートジェイ

「`assertThat(実際の値).〜`」という流れるような書き方で、検証を書けるライブラリです。メソッドを補完しながら書け、失敗時のメッセージが読みやすいのが特徴です。JUnit 標準の `assertEquals` は引数の順番を間違えやすいため、この教材では AssertJ を使います。

**初出**：[第8章 JUnitで最初のテスト](../part01/ch08-junit.md)

**関連**：[JUnit](#t-junit)

### `@Async` { #t-async }

**読み**：アシンク

メソッドを**呼び出し元とは別のスレッド**で実行するアノテーションです（`@EnableAsync` で有効化）。呼び出し側は結果を待たずに戻ります。`@Transactional` と同じくプロキシで実現されるので、同じクラスの中からの呼び出し（自己呼び出し）には効きません。別スレッドの DB 操作は元のトランザクションの外で動きます。

**初出**：[第29章 定期処理：失敗しても続けられるジョブ](../part05/ch29-scheduling.md)　**詳しくは**：[第23章 @Transactionalの正体：プロキシ](../part04/ch23-transactional-proxy.md)、[第24章 スレッドとコネクションプール（第9節 トランザクションは、スレッドに紐づく）](../part04/ch24-connection-pool.md#9-トランザクションはスレッドに紐づく)

**関連**：[プロキシ](#t-proxy)、[自己呼び出し](#t-self-invocation)、[@Scheduled](#t-scheduled)

### AutoCloseable { #t-autocloseable }

`close()` を持つ「閉じられるもの」を表す interface です。ファイル、DB 接続（`Connection`）、HTTP レスポンスなどが実装しています。`try-with-resources` なしで使っているコードは、閉じ忘れを疑います。

**初出**：[第5章 nullと例外](../part01/ch05-null-exceptions.md)

### Bean（ビーン）／DIコンテナ { #t-bean }

**読み**：ビーン

**DIコンテナ**は、部品を作り、必要な相手に渡し、管理する仕組みです（Spring では `ApplicationContext`）。コンテナが管理するオブジェクトを **Bean** と呼びます。「Spring が作って持っているオブジェクト」と考えて構いません。何も指定しなければ singleton で、アプリ全体に1つです。

**初出**：[第11章 DIコンテナとBean](../part02/ch11-di-container.md)

**関連**：[DI](#t-di)、[singleton](#t-singleton)、[自動構成](#t-autoconfig)

### Bean Validation { #t-bean-validation }

Java の検証の標準仕様（`jakarta.validation.*`）です。DTO に注釈を付けて「空でない」「この範囲」と宣言すると、`@Valid` のついた引数に対して自動で検査されます。Spring Boot では `spring-boot-starter-validation` を足すと、実装（Hibernate Validator）が付いてきます。`javax.validation.*` は古い世代です。

**初出**：[第14章 入力検証とエラーの設計](../part02/ch14-validation-errors.md)

**関連**：[DTO](#t-dto)、[ProblemDetail](#t-problem-detail)

### BOM（Bill of Materials） { #t-bom }

**読み**：ボム

「どのライブラリのどのバージョンを組み合わせれば動くか」を、動作確認済みの組として1つの表にしたものです。Spring Boot の親 POM が持っていて、表に載っているライブラリは `<version>` を**書かなくてよい**ことになります。

**初出**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[Spring Framework と Spring Boot](#t-spring-boot)

### checksum（チェックサム） { #t-checksum }

**読み**：チェックサム

ファイルの内容から計算した数値で、1文字でも変わると別の値になります。Flyway は適用したときの値を記録しておき、次の起動のときに手元のファイルの値と比べて、**適用済みのファイルの書き換え**を検出します。

**初出**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)

**関連**：[Flyway](#t-flyway)、[マイグレーション](#t-migration)

### CI（継続的インテグレーション） { #t-ci }

**読み**：シーアイ

コードを push するたびに、**自動でビルドとテストを実行する**仕組みです。「テストを回し忘れた」「自分の PC では通った」を防ぎます。GitHub では GitHub Actions が、`.github/workflows/*.yml` の定義に従って実行します。

**初出**：[第46章 リリースと復旧](../part09/ch46-release.md)

### Cookie の属性（HttpOnly・SameSite・Secure） { #t-cookie-attr }

`HttpOnly` は JavaScript から読めなくして、XSS でのトークン盗難を防ぎます。`SameSite=Lax` は他サイトからの POST などに Cookie を添えず、CSRF を防ぎます。`Secure` は HTTPS のときだけ送ります（本番で有効）。

**説明のある章**：[第32章 匿名セッション：Cookieで利用主体を識別する](../part06/ch32-anonymous-session.md)　**詳しくは**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[CSRF](#t-csrf)、[オリジン](#t-origin-site)

### CORS { #t-cors }

**読み**：コルス

他サイトの JavaScript が**応答を読めるか**を決める、ブラウザのドアマンです。サーバーを守る仕組みではなく、送信そのものは止めない場合もあります。CORS の設定ミスは、そのまま情報漏洩になります。CSRF トークンは別の層として働きます。

**説明のある章**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[プリフライト](#t-preflight)、[オリジン](#t-origin-site)、[CSRF](#t-csrf)

### CountDownLatch { #t-countdownlatch }

カウントを持つ「扉」です。`countDown()` でカウントを1つ減らし、`await()` はカウントが0になるまで待ちます。「全員が揃うまで待って、一斉に開始」を作る定番の道具で、並行性のテストでは**スタートの号砲**に使います。

**初出**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

### CSRF { #t-csrf }

**読み**：シーサーフ（シー・エス・アール・エフ）

悪意のあるサイトのページが、裏で QuickVote 宛てのリクエストを出させる攻撃です。ブラウザは宛先に合えば Cookie を**自動で添える**ので、Cookie だけで本人と判断すると削除などが通ります。対策は、正規のページだけが知る**CSRF トークン**をヘッダーに付けさせ、サーバーが照合することです。

**説明のある章**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[Cookie の属性](#t-cookie-attr)、[CORS](#t-cors)、[オリジン](#t-origin-site)

### DI（Dependency Injection、依存性の注入） { #t-di }

**読み**：ディーアイ

クラスが必要とする部品（依存）を、**自分で作らず、外から受け取る**設計です。`PollController` が `PollService` を必要とするとき、`new PollService()` と書かずコンストラクタの引数で受け取ります。第3章の「変数の型は interface、実体は差し替え可能」を、アプリ全体で使う形です。

**初出**：[第11章 DIコンテナとBean](../part02/ch11-di-container.md)

**関連**：[Bean](#t-bean)、[singleton](#t-singleton)

### DispatcherServlet { #t-dispatcher-servlet }

Spring MVC の入口となるクラスです。Filter の列を通ったリクエストを受け、宛先表（HandlerMapping）で担当の Controller のメソッドを選びます。ログには `Initializing Spring DispatcherServlet 'dispatcherServlet'` と出ます。

**説明のある章**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)　**詳しくは**：[第12章 リクエストの旅](../part02/ch12-request-journey.md)

**関連**：[Servlet／Tomcat](#t-servlet)、[Filter](#t-filter)

### Docker Compose（ドッカー・コンポーズ） { #t-docker-compose }

使うコンテナ（DB など）を `compose.yaml` というファイルに書いておき、`docker compose up` の1行でまとめて起動する仕組みです。「このプロジェクトに必要な部品」を**コードとして**共有できます。

**初出**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)

**関連**：[コンテナ／イメージ](#t-container)、[Testcontainers](#t-testcontainers)

### DTO（Data Transfer Object） { #t-dto }

**読み**：ディーティーオー

HTTP でやり取りする**データの入れ物**です。リクエストを受け取る `CreatePollRequest`、応答を返す `PollDetail` のように、**API の境界専用の型**を作ります。業務ロジックは持たず、`record` がぴったりです。Entity をそのまま返さないために使います。

**初出**：[第13章 API契約：DTOとJSON](../part02/ch13-api-contract.md)

**関連**：[record](#t-record)、[Entity](#t-entity)、[Bean Validation](#t-bean-validation)

### Entity { #t-entity }

**読み**：エンティティ

JPA で、DB の表の1行に対応づけるクラスです（`@Entity`）。永続コンテキストに管理されている間（managed）は、変更が検出されて `UPDATE` になります。new／managed／detached／removed の4つの状態があります。

**説明のある章**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[永続コンテキスト](#t-persistence-context)、[ダーティチェック](#t-dirty-checking)、[Hibernate／Spring Data JPA](#t-hibernate)、[遅延ロード](#t-lazy-loading)

### equals と hashCode の規約 { #t-equals-hashcode }

「**2つのオブジェクトが `equals` で等しいなら、`hashCode` も同じでなければならない**」という約束です。どちらか片方だけ書くとこの約束が破れて、`HashSet` や `HashMap` が壊れます（探しても見つからない、重複する、など）。文字列や ID・コードのような値の比較は、`==` ではなく必ず `equals` を使います。`record` は全フィールドで両方を自動生成します。

**初出**：[第7章 値の落とし穴：equals・数値・時刻・文字](../part01/ch07-value-pitfalls.md)

**関連**：[record](#t-record)

### expand／contract { #t-expand-contract }

1回の変更を**複数回のリリースに分けて**行う方法です。**expand（広げる）**は新しいものを足し、古いものはそのまま残します。**contract（絞る）**は、全部のアプリが新しいものだけを使うようになってから古いものを消します。

**初出**：[第46章 リリースと復旧](../part09/ch46-release.md)

**関連**：[マイグレーション](#t-migration)

### fail fast { #t-fail-fast }

間違いを、**できるだけ早い段階で、大きな音を立てて**失敗させる設計です。起動時に分かればデプロイの時点で気づけますが、実行時まで分からないと、本番で最初にその設定を使ったリクエストが（深夜に）失敗します。

**初出**：[第15章 設定・プロファイル・秘密](../part02/ch15-configuration.md)

**関連**：[プロファイル](#t-profile)

### Filter（Servlet フィルタ） { #t-filter }

**読み**：フィルター

リクエストが DispatcherServlet に届く前後に動く、**全リクエスト共通の前処理・後処理の列**です（相関ID、Spring Security など）。次へ進めるか、ここで打ち切るかを決めます。自作の Security フィルタは `@Component` にせず `SecurityConfig` の中で足します。

**説明のある章**：[第12章 リクエストの旅](../part02/ch12-request-journey.md)　**詳しくは**：[第31章 Spring Securityの地図](../part06/ch31-spring-security.md)

**関連**：[DispatcherServlet](#t-dispatcher-servlet)、[相関ID](#t-correlation-id)、[Servlet／Tomcat](#t-servlet)

### flush（フラッシュ） { #t-flush }

永続コンテキストの中の変更を、**SQL として DB に送る**ことです。送っただけでコミットではなく、コミットが失敗すれば `UPDATE` もロールバックされます。起きるのは、①コミット直前、②クエリ実行の直前（auto flush）、③`flush()` を明示的に呼んだとき、の3つです。

**初出**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[永続コンテキスト](#t-persistence-context)、[ダーティチェック](#t-dirty-checking)

### Flyway { #t-flyway }

**読み**：フライウェイ

DB のスキーマをコードで育てるマイグレーションツールです。`V1__...sql` のような版番号つき SQL ファイルを順に流し、`flyway_schema_history` に記録します。適用済みのファイルは書き換えられません。Git のようには**巻き戻さず**、間違いは新しい V で直します。

**説明のある章**：[第17章 PostgreSQLとFlyway：スキーマをコードで育てる](../part03/ch17-postgres-flyway.md)

**関連**：[マイグレーション](#t-migration)、[checksum](#t-checksum)、[expand／contract](#t-expand-contract)

### `SELECT ... FOR UPDATE`（行ロック） { #t-for-update }

「この行を更新するつもりで読む」という指定で、その行に**排他ロック**をかけます。ロックは**トランザクションが終わるまで**続き、他のトランザクションが同じ行に `FOR UPDATE` を掛けると解放されるまで待たされます。

**説明のある章**：[第25章 上限を守る：同時参加の競合とロック](../part04/ch25-locking.md)

**関連**：[悲観ロック](#t-pessimistic-lock)、[親行ロック](#t-parent-row-lock)、[デッドロック](#t-deadlock)

### GC（ガベージコレクション） { #t-gc }

**読み**：ジーシー

もう誰も参照していないオブジェクトを探して、メモリを回収する仕組みです。JavaScript にもあります。ただしサーバーでは、GC のために処理が一瞬止まったり、メモリ設定を間違えて落ちたりします。

**初出**：[第2章 Javaが動くまで：JVM・コンパイル・main](../part01/ch02-how-java-runs.md)

**関連**：[JVM](#t-jvm)

### graceful shutdown { #t-graceful }

停止の合図（SIGTERM）を受けたら、新しいリクエストは受け付けず、**処理中のものが終わるまで**待ってから止める設定です（`server.shutdown: graceful`）。待つ上限は `spring.lifecycle.timeout-per-shutdown-phase`（既定30秒）で、過ぎると強制終了します。

**説明のある章**：[第45章 性能と堅牢性：タイムアウト・負荷・停止](../part09/ch45-performance.md)　**詳しくは**：[第36章 SSE入門：サーバーから流し続ける](../part07/ch36-sse.md)

**関連**：[SSE](#t-sse)

### HandlerMethodArgumentResolver（引数リゾルバ） { #t-arg-resolver }

Controller のメソッドの**引数1つ1つを、リクエストから組み立てる部品**です。`@RequestBody` は JSON を読む部品、`@PathVariable` は URL の一部を取り出す部品。自作すると「この型の引数はこう作る」を足せます。

**初出**：[第12章 リクエストの旅](../part02/ch12-request-journey.md)

**関連**：[@RestController](#t-restcontroller)

### Hibernate／Spring Data JPA { #t-hibernate }

JPA は仕様で、**Hibernate ORM はその実装**（SQL を実際に組み立てて発行する）、**Spring Data JPA** は `JpaRepository` を提供して Hibernate を使いやすく包む Spring の部品です。呼び出しは Spring Data JPA → JPA → Hibernate → JDBC → PostgreSQL の順に深く入るので、ログに `org.hibernate...` が出ます。

**説明のある章**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[JPA](#t-jpa)、[ORM](#t-orm)、[Entity](#t-entity)

### Idempotency-Key { #t-idempotency-key }

POST などを**安全に再送**するために、クライアントが付けるヘッダーです（`crypto.randomUUID()` など。サーバーは発行しません）。「ユーザーが1回ボタンを押した」ことに1つのキーを割り当て、その再送では同じキーを使います。同じキーで中身が違うと 422 `IDEMPOTENCY_KEY_REUSED` で断ります。

**説明のある章**：[第27章 冪等性：応答が消えても二重登録しない](../part05/ch27-idempotency.md)

**関連**：[冪等性](#t-idempotency)

### identity 列 { #t-identity }

`GENERATED ALWAYS AS IDENTITY` を付けた列は、DB が 1, 2, 3, … と**自動で採番**します。`ALWAYS` なので、アプリが値を指定して入れようとすると `cannot insert a non-DEFAULT value into column "id"` のエラーになります。

**初出**：[第18章 データモデルと制約設計](../part03/ch18-data-modeling.md)

**関連**：[制約の名前](#t-constraint-name)

### IDOR（Insecure Direct Object Reference） { #t-idor }

**読み**：イドール

オブジェクトの ID（連番、UUID など）を直接指定して取得する API で、「その人に見せてよいか」を確かめていない欠陥です。ID を書き換えるだけで他人のデータが取れます。Web アプリの認可の欠陥として、最もよく見つかるものの1つです。

**初出**：[第30章 認証・識別・認可を分ける](../part06/ch30-authz-design.md)

**関連**：[識別・認証・認可](#t-id-authn-authz)

### jar（Java ARchive） { #t-jar }

**読み**：ジャー

`.class` ファイルや資源をまとめた zip 形式のファイルです。`unzip` で中身を見られます。ライブラリ（Jackson、Spring など）も jar で配布されます。Spring Boot は、これらを1つにまとめた実行可能 jar を作ります。

**初出**：[第2章 Javaが動くまで：JVM・コンパイル・main](../part01/ch02-how-java-runs.md)　**詳しくは**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[バイトコード](#t-bytecode)、[JVM](#t-jvm)

### JdbcClient { #t-jdbcclient }

**読み**：ジェイディービーシー・クライアント

SQL を書いて DB に問い合わせるための Spring の道具です（Spring Framework 6.1 で追加）。SQL は**文字列で自分で書き**、結果を Java のオブジェクトに変換する部分を手伝ってくれます。JPA のように SQL を隠さないので、何が実行されるかが読めます。

**初出**：[第19章 JdbcClientでSQLを書く](../part03/ch19-jdbcclient.md)

**関連**：[ON CONFLICT](#t-on-conflict)、[SQL インジェクション](#t-sql-injection)、[ORM](#t-orm)

### JDK（Java Development Kit） { #t-jdk }

**読み**：ジェイディーケー

Java での開発に必要な道具一式です。コンパイラ `javac`、実行コマンド `java`、標準ライブラリなどが入っています。実行するだけの JRE という言葉も見かけますが、現在は JDK だけを入れれば十分です。

**初出**：[第1章 開発環境を整える](../part00/ch01-setup.md)

**関連**：[LTS](#t-lts)、[JVM](#t-jvm)

### JIT（Just-In-Time）コンパイル { #t-jit }

**読み**：ジット

実行中に、頻繁に呼ばれるバイトコードを機械語へ変換して速くする仕組みです。V8 にも同じ仕組みがあります。Java は何日も動き続けるサーバーで威力を出す設計で、起動直後は遅く、温まると速くなります。

**初出**：[第2章 Javaが動くまで：JVM・コンパイル・main](../part01/ch02-how-java-runs.md)

**関連**：[バイトコード](#t-bytecode)、[JVM](#t-jvm)

### JPA（Jakarta Persistence） { #t-jpa }

**読み**：ジェイピーエー

Java の世界の **ORM の標準仕様**です。`@Entity` や `EntityManager` などのインターフェースと注釈を決めたもので、実装そのものではありません。以前は Java Persistence API で、パッケージも `javax.persistence.*` から **`jakarta.persistence.*`** に変わりました。

**初出**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[ORM](#t-orm)、[Hibernate／Spring Data JPA](#t-hibernate)、[Entity](#t-entity)

### JUnit { #t-junit }

**読み**：ジェイユニット

Java で最も使われるテストの枠組みです。この教材では現行の **JUnit 6**（テストを書く部分は **Jupiter**）を使います。`org.junit.jupiter.api` パッケージと書き方は JUnit 5 とほぼ同じなので、古い記事の知識も大半は使えます。

**初出**：[第8章 JUnitで最初のテスト](../part01/ch08-junit.md)

**関連**：[AssertJ](#t-assertj)

### JVM（Java Virtual Machine） { #t-jvm }

**読み**：ジェイブイエム

`.class` を読み込んで実行する、Java の実行エンジンです。「仮想」マシンというのは、Windows でも Mac でも Linux でも同じ `.class` を同じように動かせるよう、OS の違いを隠してくれるからです。Node.js の V8 が JavaScript を実行するのと同じ位置にいます。

**初出**：[第2章 Javaが動くまで：JVM・コンパイル・main](../part01/ch02-how-java-runs.md)

**関連**：[バイトコード](#t-bytecode)、[JIT](#t-jit)、[GC](#t-gc)、[JDK](#t-jdk)

### JWT { #t-jwt }

**読み**：ジェイダブリューティー

サーバーが秘密鍵で**署名**した、利用者の情報（誰か、いつまで有効か、権限）を含む文字列です。受け取った側は署名を検証するだけで、発行元に問い合わせずに中身を信用できます。**暗号化ではなく署名**なので、中身は誰でも読めます。

**初出**：[付録 ログイン型認証の概観](login-auth.md)

### Last-Event-ID { #t-last-event-id }

ブラウザの `EventSource` が、**最後に受け取った `id:`** を覚えておき、再接続のときにリクエストヘッダー `Last-Event-ID` として送り返す仕組みです。**どう続きを送るかはサーバーの仕事**で、QuickVote は DB のイベント履歴から再送します。

**初出**：[第36章 SSE入門：サーバーから流し続ける](../part07/ch36-sse.md)　**詳しくは**：[第37章 取りこぼしを防ぐ：イベント履歴とLast-Event-ID](../part07/ch37-event-history.md)

**関連**：[SSE](#t-sse)、[ハートビート](#t-heartbeat)

### LISTEN／NOTIFY { #t-listen-notify }

PostgreSQL の通知機能です。接続が `LISTEN チャンネル名` を実行しておくと、誰かが `NOTIFY` または `pg_notify()` を実行したとき、**LISTEN している接続にだけ**通知が届きます。通知はコミットの時点で配送され、ロールバックなら破棄されます。保存はされません。

**初出**：[第38章 2台構成で起きること](../part07/ch38-multi-instance.md)

**関連**：[SSE](#t-sse)、[Last-Event-ID](#t-last-event-id)

### liveness／readiness（プローブ） { #t-liveness }

**読み**：ライブネス／レディネス

実行基盤（Kubernetes やロードバランサー）が、アプリに定期的に問い合わせて状態を知るための窓口です。**liveness** が失敗するとプロセスを**再起動**し、**readiness** が失敗するとそのインスタンスへの**リクエストの振り分けを止めます**。

**初出**：[第44章 メトリクスとヘルスチェック](../part09/ch44-metrics.md)

**関連**：[Actuator／Micrometer](#t-actuator)

### LTS（長期サポート版） { #t-lts }

**読み**：エルティーエス

Java は半年ごとに新しい版が出ます。そのうち、長期間サポートされる版が LTS です。Java 25 は LTS で、この教材の基準です。

**初出**：[第1章 開発環境を整える](../part00/ch01-setup.md)

**関連**：[JDK](#t-jdk)

### Maven Wrapper { #t-maven-wrapper }

**読み**：メイヴン・ラッパー

プロジェクトに同梱された小さなスクリプト（`mvnw`、Windows は `mvnw.cmd`）です。初回に指定された版の Maven をダウンロードしてキャッシュし、以後それを使います。Node の `.nvmrc` に近い役割ですが、**指定した版を自動で取ってくる**ところまでやります。

**初出**：[第1章 開発環境を整える](../part00/ch01-setup.md)

**関連**：[JDK](#t-jdk)

### MDC（Mapped Diagnostic Context） { #t-mdc }

**読み**：エムディーシー

ログ用の**スレッド専用のメモ帳**です。キーと値を入れておくと、そのスレッドが出すログに自動で添えられます。Node の `AsyncLocalStorage` に近い役割です。スレッドを離れた処理（`@Scheduled` など）のログには付きません。

**初出**：[第43章 ログ：あとから原因を追えるように](../part09/ch43-logging.md)

**関連**：[ThreadLocal](#t-thread-local)、[相関ID](#t-correlation-id)、[構造化ログ／ECS](#t-structured-log)

### N+1問題 { #t-n-plus-one }

**読み**：エヌプラスワンもんだい

親を取る1本の SQL のあと、子を取る SQL が**親の件数（N）だけ**追加で流れる状態です。1件ずつの往復が積み上がるので、件数に比例して遅くなります。DB が別サーバーなら、1往復が1ミリ秒でも、100件で約100ミリ秒、1000件で約1秒です（試算）。

**初出**：[第40章 N+1と取得戦略](../part08/ch40-n-plus-one.md)

**関連**：[遅延ロード](#t-lazy-loading)、[open-in-view](#t-osiv)、[ORM](#t-orm)

### `ON CONFLICT`（upsert） { #t-on-conflict }

「入れようとして UNIQUE 制約に衝突したら、代わりに更新する」を SQL 1本で行う PostgreSQL の書き方です。`EXCLUDED` は「入れようとした行」を指します。アプリで「あるか確認 → なければ入れる」と書く代わりに DB に判断を任せるので、確認と更新の間に割り込まれません。

**初出**：[第19章 JdbcClientでSQLを書く](../part03/ch19-jdbcclient.md)　**詳しくは**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[JdbcClient](#t-jdbcclient)、[インデックス](#t-index)

### open-in-view（OSIV） { #t-osiv }

**読み**：オーエスアイブイ

リクエストの最初から最後まで（Controller、JSON 化まで）、**EntityManager を開きっぱなしにする**仕組みです。Spring Boot では**既定で有効**（`spring.jpa.open-in-view` が未設定なら `true`）で、起動時に警告ログが出ます。`LazyInitializationException` が消えるかわりに、DB 接続を握り続けます。

**初出**：[第40章 N+1と取得戦略](../part08/ch40-n-plus-one.md)

**関連**：[遅延ロード](#t-lazy-loading)、[コネクションプール](#t-connection-pool)、[永続コンテキスト](#t-persistence-context)

### ORM（Object-Relational Mapping） { #t-orm }

**読み**：オーアールエム

DB の表の行を、プログラミング言語のオブジェクトに対応づける技術です。「オブジェクトと表のずれ（たとえばオブジェクトは入れ子になるが、表は平らな行）を埋める」ことが仕事です。

**初出**：[第39章 JPAは何を肩代わりするのか](../part08/ch39-jpa-basics.md)

**関連**：[JPA](#t-jpa)、[Hibernate／Spring Data JPA](#t-hibernate)、[JdbcClient](#t-jdbcclient)

### ProblemDetail（RFC 9457） { #t-problem-detail }

HTTP API のエラーを表す標準の JSON の形です（旧 RFC 7807）。Content-Type は `application/problem+json`。決まった項目は `type`、`title`、`status`、`detail`、`instance` で、**独自の項目（拡張）を足せます**（QuickVote は `code`）。Spring は `ProblemDetail` クラスで対応しています。

**初出**：[第14章 入力検証とエラーの設計](../part02/ch14-validation-errors.md)

**関連**：[@RestControllerAdvice](#t-rest-controller-advice)、[Bean Validation](#t-bean-validation)

### record { #t-record }

データを運ぶだけのクラスを**1行で**書くための構文です。`private final` のフィールド、コンストラクタ、アクセサ（`label()`）、全フィールドを比べる `equals`／`hashCode`、`toString` を自動で作ります。不変に見えますが、中に `List` などが入っていれば変更できてしまいます。

**説明のある章**：[第4章 データの形：record・enum・sealed・switch](../part01/ch04-data-shapes.md)

**関連**：[DTO](#t-dto)、[sealed](#t-sealed)

### `@RestController` { #t-restcontroller }

HTTP リクエストを受けるクラスの目印です。メソッドの戻り値は**JSON に変換されて応答の本文になります**。`@Controller` は HTML 画面（テンプレート）用で、本教材では使いません。

**初出**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[@RestControllerAdvice](#t-rest-controller-advice)、[HandlerMethodArgumentResolver](#t-arg-resolver)、[アノテーション](#t-annotation)

### `@RestControllerAdvice` { #t-rest-controller-advice }

すべての Controller に共通の「例外の受け皿」を作るクラスの目印です。`@ExceptionHandler(例外の型)` を付けたメソッドが、その例外を受けて HTTP 応答に変換します。リクエストの地図では「例外が起きたら Controller の外へ飛び出し、ここで受ける」場所です。

**初出**：[第14章 入力検証とエラーの設計](../part02/ch14-validation-errors.md)

**関連**：[ProblemDetail](#t-problem-detail)、[@RestController](#t-restcontroller)

### RPO／RTO { #t-rpo-rto }

**読み**：アールピーオー／アールティーオー

**RPO**（復旧時点目標）は、どれだけ前のデータまで失ってよいか。1日1回のバックアップなら最大1日分を失います。**RTO**（復旧時間目標）は、止まってからどれだけで復旧するか。バックアップの頻度と方式は、この2つから逆算して決めます。

**初出**：[第46章 リリースと復旧](../part09/ch46-release.md)

### `@Scheduled`（`fixedDelay`・`fixedRate`・`cron`） { #t-scheduled }

メソッドを**定期的に呼び出す**アノテーションです（`@EnableScheduling` で有効）。`fixedDelay` は前回の**終了から**、`fixedRate` は前回の**開始から**の間隔、`cron` は「毎日3時」のような時刻指定です。終了を待ちたい掃除には `fixedDelay` を使います。

**初出**：[第29章 定期処理：失敗しても続けられるジョブ](../part05/ch29-scheduling.md)

**関連**：[アドバイザリロック](#t-advisory-lock)、[@Async](#t-async)、[アノテーション](#t-annotation)

### sealed（シールド） { #t-sealed }

継承・実装できるクラスを**限定する**機能で、「閉じたユニオン」を作れます。`permits` に並べた型以外は `implements` できません。コンパイラが「ケースはこれで全部」と知るので、`switch` の網羅チェックができます。`default` を書くとこのチェックが無効になります。

**初出**：[第4章 データの形：record・enum・sealed・switch](../part01/ch04-data-shapes.md)

**関連**：[パターンマッチング](#t-pattern-matching)、[record](#t-record)

### Servlet／Tomcat { #t-servlet }

**読み**：サーブレット／トムキャット

**Servlet** は HTTP リクエストを受け取って応答を返す Java 標準の仕組み（`jakarta.servlet.*`）で、**Tomcat** はそれを動かす HTTP サーバー（Servlet コンテナ）です。Spring Boot は Tomcat をアプリの中に**埋め込んで**、`java -jar` だけで起動できるようにしています。

**初出**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[Spring Framework と Spring Boot](#t-spring-boot)、[DispatcherServlet](#t-dispatcher-servlet)、[Filter](#t-filter)

### singleton（シングルトン） { #t-singleton }

**読み**：シングルトン

「アプリ全体で1つだけ作られる」という意味です。Spring の Bean は、何も指定しなければ singleton で、リクエストごと・呼び出しごとに作り直されることはありません。だからフィールドにリクエスト固有の値を置くと、全員で共有されて事故になります。

**初出**：[第11章 DIコンテナとBean](../part02/ch11-di-container.md)

**関連**：[Bean](#t-bean)、[競合状態](#t-race-condition)

### Spring Framework と Spring Boot { #t-spring-boot }

**Spring Framework** はサーバーアプリを作るための部品集（DI コンテナ、Spring MVC、検証、テスト支援など）です。**Spring Boot** はそれを「設定なしで動く状態」にまとめる仕組みで、起動、自動構成、スターター、設定ファイル、依存のバージョン表（BOM）を提供します。教材で「Spring」と書くときは両方を指します。

**初出**：[第10章 Spring Bootとは：最初のAPIと起動の裏側](../part02/ch10-spring-boot.md)

**関連**：[自動構成](#t-autoconfig)、[BOM](#t-bom)、[Servlet／Tomcat](#t-servlet)

### SQL インジェクション { #t-sql-injection }

**読み**：エスキューエル・インジェクション

入力を**文字列連結で SQL に埋め込む**と、入力に含まれた SQL の断片が命令として実行されてしまう欠陥です。防ぐには、値を必ず**バインド変数**（`:name` や `?`）として渡します。Bean Validation で入力を検証していても、規則を守る代わりにはなりません。

**説明のある章**：[第19章 JdbcClientでSQLを書く](../part03/ch19-jdbcclient.md)

**関連**：[JdbcClient](#t-jdbcclient)、[Bean Validation](#t-bean-validation)

### SSE（Server-Sent Events） { #t-sse }

**読み**：エスエスイー

サーバーからブラウザへ、**片方向に出来事を流し続ける**仕組みです。特別なプロトコルではなく、**ふつうの HTTP の応答が終わらないだけ**で、Cookie も HTTPS もプロキシも今までの知識がそのまま効きます。切れたらブラウザが自動で再接続し、`Last-Event-ID` で続きを求めます。

**説明のある章**：[第36章 SSE入門：サーバーから流し続ける](../part07/ch36-sse.md)　**詳しくは**：[第37章 取りこぼしを防ぐ：イベント履歴とLast-Event-ID](../part07/ch37-event-history.md)、[第38章 2台構成で起きること](../part07/ch38-multi-instance.md)

**関連**：[Last-Event-ID](#t-last-event-id)、[ハートビート](#t-heartbeat)、[LISTEN／NOTIFY](#t-listen-notify)

### Testcontainers（テストコンテナーズ） { #t-testcontainers }

テストの中から Docker を操作して、テスト用のコンテナ（本物の DB など）を起動・破棄するライブラリです。`docker compose up` を手で打つ必要はなく、Docker Desktop が起動していれば `./mvnw verify` だけで動きます。

**初出**：[第21章 実PostgreSQLでテストする](../part03/ch21-testcontainers.md)

**関連**：[Docker Compose](#t-docker-compose)、[コンテナ／イメージ](#t-container)

### ThreadLocal { #t-thread-local }

**読み**：スレッドローカル

**スレッドごとの変数**です。「1リクエスト＝1スレッド」なので、リクエスト固有の情報を安全に持てます（MDC、トランザクションの接続など）。そのかわり、スレッドを跨ぐと外れ、スレッドプールでは**消し忘れる**と次のリクエストに前の値が残ります。

**説明のある章**：[第12章 リクエストの旅](../part02/ch12-request-journey.md)　**詳しくは**：[第24章 スレッドとコネクションプール（第9節 トランザクションは、スレッドに紐づく）](../part04/ch24-connection-pool.md#9-トランザクションはスレッドに紐づく)

**関連**：[MDC](#t-mdc)、[トランザクション](#t-transaction)

### TLS 終端 { #t-tls-termination }

**読み**：ティーエルエス・しゅうたん

手前のリバースプロキシ（nginx、ロードバランサー）が HTTPS（TLS）を終わらせて、アプリには HTTP で渡す構成です。アプリはリクエストが HTTPS だったことを知らないので、`X-Forwarded-Proto` を信用する設定（`server.forward-headers-strategy`）が要ります。

**説明のある章**：[第34章 ブラウザとの境界：Cookie属性・CSRF・CORS・HTTPS](../part06/ch34-browser-boundary.md)

**関連**：[Cookie の属性](#t-cookie-attr)

### write skew { #t-write-skew }

**読み**：ライト・スキュー

読んだ結果に基づいて、**別々の行**を書き、全体として条件が破れる現象です。REPEATABLE READ は「同じ行の競合」しか検出しないので、「49人と読んで別々の行を足し、51人になる」は防げません。SERIALIZABLE なら DB が検出して片方を失敗させます。

**説明のある章**：[第26章 競合の別解と並行性テスト](../part04/ch26-concurrency-testing.md)

**関連**：[分離レベル](#t-isolation)、[親行ロック](#t-parent-row-lock)、[競合状態](#t-race-condition)

---

## 出典と更新

この用語集は、全49章の用語の枠をスクリプトで収集して作りました。章のコードと説明が変わったときは、章の用語の枠を直したうえで、ここの定義を合わせてください。
