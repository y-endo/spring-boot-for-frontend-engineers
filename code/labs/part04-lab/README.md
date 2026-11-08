# 第4部のラボ：トランザクションと同時実行（第22〜26章）

QuickVote と同じ表（Flyway の V1・V2。リファレンス実装からコピー）に、同じ形の SQL を投げる最小のアプリです。
各章の実験を、再現できる形でまとめています。Java 25 / Spring Boot 4.1.2 / PostgreSQL 18。

```bash
docker compose up -d                  # PostgreSQL（ホスト側 25420。LAB_DB_PORT で変更可）。log_statement=all
./mvnw -q -DskipTests package         # Windows は .\mvnw.cmd
java -jar target/part04-lab.jar       # 初回は Flyway が表を作る。Started と出たら Ctrl+C
./mvnw verify                         # 並行性のテスト（Docker 必須）
docker compose down                   # 片付け
```

## 章ごとの実験

| 章 | 実験 | コマンド |
| --- | --- | --- |
| 22 | psql で BEGIN / COMMIT / ROLLBACK | `./scripts/scenario.sh scenarios/22-begin-rollback.txt`、`22-aborted.txt` |
| 22 | 2本の INSERT の間で例外（`@Transactional` の有無） | `java -jar target/part04-lab.jar --lab.run=ch22 --spring.main.web-application-type=none --logging.level.org.springframework.jdbc.support.JdbcTransactionManager=DEBUG` |
| 22・23 | サーバーが受け取った SQL（BEGIN / COMMIT / ROLLBACK） | `docker exec part04-lab-pg psql -U lab -d lab -qc "SELECT 'LAB-MARK'"` → アプリ実行 → `./scripts/pgtx.sh` |
| 23 | `@Transactional` が効く場合・効かない場合（12シナリオ） | `… --lab.run=ch23 --spring.main.web-application-type=none --logging.level.org.springframework.jdbc.support.JdbcTransactionManager=DEBUG` |
| 24 | プール枯渇・トランザクション中の外部通信・REQUIRES_NEW・仮想スレッド | `java -jar target/part04-lab.jar --server.port=18024 --spring.datasource.hikari.maximum-pool-size=3 --spring.datasource.hikari.connection-timeout=2000` のあと `curl localhost:18024/lab/ch24/slow?sec=3` など。`--spring.threads.virtual.enabled=true` で比較 |
| 25 | 2つの psql で「残り1枠に2人」 | `./scripts/scenario.sh scenarios/25-last-seat-race.txt`、`25-last-seat-lock.txt`、`25-other-path.txt` |
| 25 | 負荷（20人同時） | アプリを `--server.port=18025` で起動し、`java scripts/LoadJoin.java http://localhost:18025 unsafe\|tx-only\|locked 20` |
| 25 | リファレンス実装への負荷 | `code/quickvote` をビルドして起動し、`java scripts/LoadJoinQuickVote.java http://localhost:<port> 20` |
| 26 | 楽観ロック・ON CONFLICT・分離レベル・SERIALIZABLE・デッドロック | `scenarios/26-*.txt` を `scenario.sh` で実行 |
| 26 | `CountDownLatch` による並行性テスト | `./mvnw verify`（`JoinRaceIT`、`ConflictIT`） |

`scripts/TwoSessions.java` は、複数の `docker exec -i psql` を台本どおりの順に動かし、出力を到着順に並べます。
実際には、ターミナルを2つ開いて `docker exec -it part04-lab-pg psql -U lab -d lab` で入り、交互に打っても同じです。

`outputs/` に、本文に載せた出力の全文（または元データ）があります。タイミングに依存する数字（負荷の成功数、再試行の回数、`LUCK` の回数）は、実行のたびに変わります。
