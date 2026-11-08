# 第9部 運用：公開後に責任を持つ

ここまでで QuickVote は「正しく動く」ようになりました。この部では、**動かし続ける**ための仕事をします。公開したあとに起きるのは、たいてい次のような出来事です。

- 夜中にアプリが遅くなった。**何が起きているのか**、どこを見ればよいか
- 誰かが「投票できない」と言ってきた。**そのリクエストのログ**を、どうやって探すか
- DB が数分止まった。**アプリは、何を返し、どう復旧するか**
- 新しいバージョンを出したい。**止めずに**、**戻せる形で**

コードが正しいことと、サービスを運用できることは別の能力です。この部は、運用の道具（Docker、ログ、メトリクス、タイムアウト、CI、バックアップ）を、**実際に動かして、壊して、観察する**ことで身につけます。

## この部でできるようになること

- マルチステージ・layered jar・非rootの `Dockerfile` を読み、イメージのサイズと再ビルドの速さを `docker history` で説明できる
- コンテナのメモリ制限が JVM のヒープ上限にどう反映されるかを、`-XX:+PrintFlagsFinal` で確かめて設定できる
- 構造化ログ（ECS）に相関ID（`X-Request-Id`）を載せ、1つのリクエストのログだけを追える
- **ログに本文・トークン・参加コードを出さない**方針を、「許可した項目だけ出す」設計とテストで守れる。例外のメッセージに DB の入力値が混ざる罠を説明できる
- Actuator と Micrometer で、HikariCP と HTTP の指標を `curl` で読み、カーディナリティ（タグの値の数）に注意して指標を足せる
- readiness と liveness の違いを、「DB が止まったとき」の挙動で説明できる。メトリクスを外に出さない構成（管理ポート分離）を説明できる
- 「全部のタイムアウト」を表にして決め、**決めていないタイムアウトが事故になる**様子を再現できる
- graceful shutdown、k6 による負荷試験、スレッドダンプ（`jcmd` と `SIGQUIT`）で、遅さと詰まりの原因を絞り込める
- CI（GitHub Actions）の設定を読み、expand/contract で列の変更を安全に進め、`pg_dump` / `pg_restore` で**復元できること**を確かめられる

## アプリの到達状態

QuickVote のコード（`code/quickvote/`）は、この部では**1か所だけ**増えます。`.github/workflows/ci.yml`（第46章）です。ほかは、すでにリファレンス実装に入っている運用の仕組み（`Dockerfile`、`compose.app.yaml`、`RequestIdFilter`、`SafeThrowable`、Actuator の設定）を**読み解いて、動かして、確かめます**。

| 運用の項目 | リファレンス実装の状態 |
| --- | --- |
| 配布の形 | マルチステージ・layered jar・非root の Docker イメージ。`compose.app.yaml` でアプリ＋DB |
| ログ | ECS 形式の JSON。相関ID。許可した項目だけ出力。`LoggingIT` が漏れを検出 |
| ヘルスとメトリクス | 本番は管理ポート 9090 のみ（外部非公開）。readiness は DB を含む。`ReadinessIT`、`ProductionActuatorIT` |
| タイムアウトと停止 | Tomcat の書き込み、graceful shutdown は設定済み。**DB 側のタイムアウトは未設定**（第45章で決める） |
| リリース | CI（`ci.yml`）で `./mvnw verify` と `docker build` |

## 各章

<div class="grid cards" markdown>

-   :material-docker: **[第42章 Dockerでパッケージング](ch42-docker.md)**

    ---

    マルチステージ、layered jar（地層）、非root。イメージサイズと起動時間を実測し、JVM のメモリとコンテナ制限を `PrintFlagsFinal` で確かめます。

-   :material-text-search: **[第43章 ログ：あとから原因を追えるように](ch43-logging.md)**

    ---

    構造化ログ、相関ID、本文を出さない設計。例外のメッセージに入力値が入る罠と `SafeThrowable`。

-   :material-chart-line: **[第44章 メトリクスとヘルスチェック](ch44-metrics.md)**

    ---

    Actuator と Micrometer。HikariCP と HTTP の指標を読む。DB を止めて readiness が 503 になるのを見る。管理ポート分離。

-   :material-timer-sand: **[第45章 性能と堅牢性：タイムアウト・負荷・停止](ch45-performance.md)**

    ---

    p95/p99、タイムアウトの全体表、k6 の負荷試験、graceful shutdown、スレッドダンプ。

-   :material-rocket-launch-outline: **[第46章 リリースと復旧](ch46-release.md)**

    ---

    GitHub Actions、expand/contract、バックアップと復元、依存の更新。第0〜9部の総まとめ「公開前チェックリスト」。

</div>

## 第9部の流れ

```mermaid
flowchart LR
    A["第42章<br/>運べる形にする<br/>（イメージ）"] --> B["第43章<br/>あとから追える<br/>（ログ）"]
    B --> C["第44章<br/>いま見える<br/>（メトリクス）"]
    C --> D["第45章<br/>限界を決める<br/>（タイムアウト・負荷）"]
    D --> E["第46章<br/>出して戻せる<br/>（リリース・復旧）"]
```

第42章で「どこでも同じに動く箱」を作り、第43〜44章でその中を**外から見えるようにし**、第45章で**壊れ方を決め**、第46章で**出し方と戻し方**を決めます。

## 実験コードの置き場

```text
code/labs/
├── ch42-docker/        compose の上書き（ポート付け替え）、PrintFlagsFinal・メモリ制限の実験スクリプト
├── ch43-logging/       相関IDの追跡、例外メッセージの罠（NaiveLogDemo.java）、ログレベルの切り替え
├── ch44-metrics/       メトリクスの読み取り、DB 停止の観察（db-outage.sh）
├── ch45-performance/   k6 の負荷試験（vote.js）、ロック・タイムアウト・停止の実験
└── ch46-release/       expand/contract のマイグレーション、バックアップと復元の手順
code/quickvote/.github/workflows/ci.yml    第46章の CI ワークフロー
```

実験は Docker Desktop が必要です。本文のコマンドは `localhost:8080` で書いています（筆者は他の作業とのポート衝突を避けるため、別のポートに付け替えて実行しました）。

## 第9部のチェックリスト

第46章を終えたら、次の項目を確かめてください。**説明できない項目は、白紙に自分の言葉で書いてみて、書けなければ該当章へ戻ります。** 目安は「③ 検証できる」です。

- [ ] マルチステージビルドで、ビルド用イメージと実行用イメージを分ける理由を言える
- [ ] layered jar が何を分けていて、コードを1行直したときにどの層だけが作り直されるかを `docker history` で示せる
- [ ] 非 root で動かす理由と、`docker exec <コンテナ> id` で確かめる方法を言える
- [ ] コンテナのメモリ制限を付けたとき、JVM のヒープ上限が何で決まるかを `PrintFlagsFinal` で確かめ、`MaxRAMPercentage` を決められる
- [ ] 構造化ログ（ECS）の1行を読み、`requestId` で1つのリクエストのログを抜き出せる
- [ ] ログに出してよい項目を「許可リスト」で決める理由と、出してはいけないもの（本文・トークン・参加コード・Cookie・URL）を挙げられる
- [ ] `log.error("...", ex)` が原因（cause）のメッセージまで出力する理由と、DB のエラーに入力値が入る例を示せる
- [ ] 例外をログに出す場所を1か所に決める理由（二重に出さない）を説明できる
- [ ] `curl` で HikariCP と HTTP の指標を読み、`pending` が増える意味を言える。タグに入れてはいけない値（ID・URL）を挙げられる
- [ ] readiness と liveness の違いを、「DB が止まったとき」にどちらが 503 になるべきかで説明できる
- [ ] metrics を利用者の届く場所に出さない理由と、Boot で管理ポートを分けると本体のポートから Actuator が消えることを説明できる
- [ ] HTTP 接続・読み取り、Hikari の接続取得、`statement_timeout`、SSE 送信、Tomcat の接続タイムアウトの役割の違いを表にでき、決めていないものを指摘できる
- [ ] p95 / p99 が平均より大事な理由を説明し、k6 の閾値（`thresholds`）で合格ラインを書ける
- [ ] graceful shutdown で、処理中のリクエストが終わるのを待つ様子を再現でき、SSE があると停止が長引く理由を言える
- [ ] スレッドダンプを取り、`http-nio-*-exec-*` のスレッドが何を待っているかを読める
- [ ] 稼働中に列を消す・名前を変える変更が壊れる理由を再現でき、expand/contract の3段階で直せる
- [ ] `pg_dump` / `pg_restore` で別のDBに復元し、**復元後のアプリが起動できること**まで確かめられる

!!! tip "統合課題：事故対応ドリル"
    第45章の「ロックを握ったままのトランザクション」を再現し、次の順で**一人で**対応してください。

    1. 利用者の報告「投票が返ってこない」から始める。メトリクス（`hikaricp.connections.pending`）で異常に気づく
    2. スレッドダンプで、リクエストのスレッドが DB を待っていることを確かめる
    3. 相関ID でログを引き、どのリクエストが最初に詰まったかを探す
    4. 原因（ロックを握るトランザクション）を DB 側で特定し、解放する
    5. 再発防止として、`statement_timeout` を決めて設定し、同じ状況で**早く失敗する**ことを確かめる
