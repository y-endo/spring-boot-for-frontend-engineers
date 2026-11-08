# 第17章の実験：PostgreSQL（Docker Compose）と Flyway

Spring Initializr（`jdbc` `flyway` `postgresql` `docker-compose`）で作った最小のプロジェクトです。Web は入っていないので、起動してマイグレーションを流したら終了します。

```bash
./mvnw spring-boot:run          # Windows は .\mvnw.cmd。compose.yaml の PostgreSQL が自動で起動し、V1 が適用される
docker compose exec postgres psql -U quickvote -c 'SELECT * FROM flyway_schema_history'
docker compose down -v          # 後片付け（データも消す）
```

`experiments/` には、章で試す SQL が入っています。`src/main/resources/db/migration/` にコピーして、再実行してください。
修正したら `./mvnw clean ...` で `target/classes` の古いコピーを消すこと（消したファイルが `target/` に残って、Flyway に見つかり続けます）。
