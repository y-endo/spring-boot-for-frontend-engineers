#!/bin/sh
# 動作確認用：投票を1つ作って、応答を表示する。第42〜45章の実験で共通に使う。
#   使い方: BASE=http://localhost:18042 sh smoke.sh
BASE=${BASE:-http://localhost:8080}
JAR=$(mktemp)
# 最初のGETで XSRF-TOKEN の Cookie が出る（csrf.spa()）。更新系はこの値をヘッダーに入れて返す。
curl -sS -c "$JAR" -b "$JAR" "$BASE/" > /dev/null
# Windows の curl は Cookie ファイルの行末が CRLF なので、\r を取り除く
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR" | tr -d '\r')
# 日本語を含む本文は、コマンドラインに直接書かずファイルで渡す（Windows の Git Bash では引数の文字コードが壊れて 400 になる）
BODY=$(mktemp)
cat > "$BODY" <<'JSON'
{"title":"ランチはどこにする？","closesInMinutes":60,"allowMemberOptions":true,"options":["カレー","ラーメン","そば"]}
JSON
curl -sS -c "$JAR" -b "$JAR" -X POST "$BASE/api/polls" \
  -H 'Content-Type: application/json; charset=utf-8' -H "X-XSRF-TOKEN: $XSRF" \
  -H "X-Request-Id: ${REQUEST_ID:-smoke-001}" \
  --data-binary @"$BODY"
echo
rm -f "$JAR" "$BODY"
