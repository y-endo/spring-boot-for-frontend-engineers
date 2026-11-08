#!/usr/bin/env bash
# 第20章の実験環境を作る。PostgreSQL 18 を 25320 番ポートで起動し、スキーマ（Flyway の V1・V2 と同じ SQL）と
# 実験用データを入れる。後片付け: docker rm -f ch20-pg
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
MIG=../part03-quickvote-jdbc/src/main/resources/db/migration

docker rm -f ch20-pg >/dev/null 2>&1 || true
docker run -d --name ch20-pg -e POSTGRES_DB=quickvote -e POSTGRES_USER=quickvote -e POSTGRES_PASSWORD=quickvote \
  -p 25320:5432 postgres:18-alpine >/dev/null
until docker exec ch20-pg pg_isready -U quickvote -d quickvote >/dev/null 2>&1; do sleep 1; done
sleep 2
for f in "$MIG"/V1__*.sql "$MIG"/V2__*.sql; do
  docker exec -i ch20-pg psql -q -U quickvote -d quickvote -v ON_ERROR_STOP=1 < "$f"
done
time docker exec -i ch20-pg psql -q -U quickvote -d quickvote < sql/seed.sql
docker exec ch20-pg psql -U quickvote -d quickvote -c "SELECT (SELECT count(*) FROM polls) AS polls, (SELECT count(*) FROM options) AS options, (SELECT count(*) FROM participants) AS participants, (SELECT count(*) FROM votes) AS votes"
