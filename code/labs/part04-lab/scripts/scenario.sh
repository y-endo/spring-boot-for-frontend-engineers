#!/bin/sh
# 2セッション再現: 表を初期化 → seed.sql を流す → 台本を実行して、到着順の出力を表示する。
# 使い方: ./scripts/scenario.sh scenarios/25-last-seat-race.txt
# 前提: docker compose up -d 済み、アプリを一度起動して Flyway でスキーマを作ってある。
cd "$(dirname "$0")/.." || exit 1
./scripts/reset.sh 2>&1 | grep -v NOTICE
docker exec -i part04-lab-pg psql -U lab -d lab -q < scenarios/seed.sql
java -Dstdout.encoding=UTF-8 scripts/TwoSessions.java "$1"
