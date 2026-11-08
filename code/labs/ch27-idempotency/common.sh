#!/bin/sh
# 共通の部品。リファレンス実装（code/quickvote の jar）を BASE で動かしている前提。
BASE="${BASE:-http://localhost:18027}"
JAR="${JAR:-/tmp/ch27-jar.txt}"
PSQL="docker exec -i ch27-idempotency-pg psql -U quickvote -d quickvote"

# Cookie jar から XSRF-TOKEN の値を取り出す（更新系は X-XSRF-TOKEN ヘッダーに同じ値を入れる）
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$JAR" | tail -1; }

# api METHOD PATH [JSON] [追加の curl 引数...]
# JSON は標準入力から渡す（Windows のコマンドラインは日本語の文字コードが壊れることがあるため）。
api() {
  method="$1"; path="$2"; body="$3"; shift 3
  if [ -n "$body" ]; then
    printf '%s' "$body" | curl -s -b "$JAR" -c "$JAR" -X "$method" "$BASE$path" \
      -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' --data-binary @- "$@"
  else
    curl -s -b "$JAR" -c "$JAR" -X "$method" "$BASE$path" -H "X-XSRF-TOKEN: $(xsrf)" "$@"
  fi
}
