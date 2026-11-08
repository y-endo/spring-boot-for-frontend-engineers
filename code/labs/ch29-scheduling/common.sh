#!/bin/sh
# 共通の部品。リファレンス実装（code/quickvote の jar）を、この章の DB（25429）につないで動かす。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
PSQL="docker exec -i ch29-scheduling-pg psql -U quickvote -d quickvote"
JAR_FILE="${JAR_FILE:-code/quickvote/target/quickvote.jar}"   # リポジトリのルートからの相対パス

# start_app PORT LOGFILE [追加の引数...]
start_app() {
  port="$1"; logfile="$2"; shift 2
  java -jar "$JAR_FILE" --spring.docker.compose.enabled=false \
    --spring.datasource.url=jdbc:postgresql://localhost:25429/quickvote \
    --spring.datasource.username=quickvote --spring.datasource.password=quickvote \
    --server.port="$port" "$@" > "$logfile" 2>&1 &
}

# ECS 形式（JSON）のログから、削除処理に関する行だけを「時刻 [スレッド] レベル メッセージ（例外があれば型）」で取り出す
cleaner_log() {
  grep 'ExpiredDataCleaner' "$1" | sed -E 's/.*"@timestamp":"[^T]*T([0-9:]*)[^"]*","log":\{"level":"([A-Z]*)".*"thread":\{"name":"([^"]*)"\}.*"node":\{\}\},"message":"([^"]*)"(,"error":\{"type":"[^"]*","message":"([^"]*)")?.*/\1 [\3] \2 \4 \6/'
}
