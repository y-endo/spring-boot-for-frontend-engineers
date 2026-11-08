# 第3部のラボ：PostgreSQL に保存する QuickVote

第2部のラボ（`code/labs/part02-quickvote-inmemory/`、メモリ上の Map）をコピーして、Repository の中身を **JdbcClient ＋ PostgreSQL 18** に入れ替えたものです。
スキーマは Flyway（`src/main/resources/db/migration/`）が作ります。テストは Testcontainers の実 PostgreSQL で動きます。
クラス名・メソッド名・エラーコードは完成形（`code/quickvote/`）と揃えてあり、Service と Controller の変更は最小限です。

```bash
./mvnw test                  # Docker 不要のテストだけ（単体・@WebMvcTest）
./mvnw verify                # すべて（*IT は Testcontainers で実 PostgreSQL を使う。Docker 必須）
./mvnw spring-boot:run       # 起動（ポート 18019）。compose.yaml の PostgreSQL（ホスト側 25419）が自動で起動する
```

Windows は `.\mvnw.cmd`。JDK 25 と Docker Desktop が必要です。アプリを止めても DB のコンテナは動いたままです（`docker compose down -v` で、データごと消せます）。
DB には `docker compose exec postgres psql -U quickvote` で入れます。

## 第2部のラボとの差

| 項目 | 第2部（メモリ） | 第3部（このラボ） |
| --- | --- | --- |
| 保存先 | `ConcurrentHashMap` | PostgreSQL 18（Docker Compose。Flyway で V1・V2） |
| Repository | Map ＋ `AtomicLong` | `JdbcClient` ＋ SQL。メソッド名・引数・戻り値は同じ |
| 参加コード重複 | `PollRepository.DuplicateJoinCodeException`（自前） | `DuplicateKeyException`（UNIQUE 制約違反が変換されたもの）。`PollService` で取り直す |
| 時刻 | `clock.instant()` | `Db.now(clock)`（マイクロ秒に丸める。往復で値が変わらない） |
| `JoinService` | 確認 → 挿入 | 同じ。ただし挿入で `DuplicateKeyException` が出たら再参加と同じ扱いに翻訳（第19章） |
| `ProblemDetailAdvice` | — | `DuplicateKeyException` → 409 `ALREADY_EXISTS` の受け皿を追加（第19章。完成形にはない） |
| `Poll.lastEventSeq` | 常に 0 | 常に 0（`polls.last_event_seq` 列は第37章で足す） |
| 訪問者 | `X-Visitor-Id` の数値 | 同じ。`participants.visitor_id` は外部キーなしの数値（`visitors` 表は第6部） |
| テスト | `PollServiceTest` は Repository を `new` で手組み | `PollServiceIT` ほか。実 PostgreSQL。テストごとに `TRUNCATE`（`AbstractIntegrationTest`） |

## 完成形との差（この時点の保証）

| 項目 | このラボ | いつ直るか |
| --- | --- | --- |
| 複数のSQLをまたいだ「全部成功か全部なしか」 | `create` は投票・参加者・選択肢を別々のコミットで書く。途中で失敗すると投票だけ残る | 第22章 |
| 人数・選択肢の上限 | 「数える → 足す」の間は守られていない。DB の制約では書けない条件 | 第25章 |
| 選択肢追加の再送 | `Idempotency-Key` なし。再送で二重登録 | 第27章 |
| 訪問者の特定 | `X-Visitor-Id`（偽装できる） | 第6部 |

## Flyway マイグレーション

| ファイル | 内容 |
| --- | --- |
| `V1__create_polls_and_options.sql` | 完成形の V1 と同じ（`polls`・`options`） |
| `V2__create_participants_and_votes.sql` | 完成形の V2 から `visitors` と `visitor_id` の外部キーを除いたもの |

## 章との対応

| テスト・コード | 章 |
| --- | --- |
| `db/migration/V1`、`V2` | 第17章・第18章 |
| `SchemaConstraintIT` | 第18章（制約を直接叩く。51人目も入ってしまう実験を含む） |
| `PollRepositoryIT`、`demo/JdbcClientTourIT`、`demo/SqlInjectionDemoIT`、`poll/JoinRaceTranslationIT` | 第19章 |
| `demo/KeysetPagingIT` | 第20章（キーセット方式のページング） |
| `AbstractIntegrationTest`、`FlywayMigrationIT`、`PollRepositorySliceIT`、`QuickVoteFlowIT`、`PollServiceIT`、`demo/OrderDependentDemoIT`（`QV_DEMO_BROKEN=1` のときだけ動く） | 第21章 |

第2部から引き継いだテスト（`di/`、`config/`、`journey/`、`json/` ほか）も、DB につながる版で動きます。
`journey/JourneyTraceConfig` は、`@Repository` の Bean が例外変換の代理に包まれて渡されるため、元のクラスで判定するように直しました。
