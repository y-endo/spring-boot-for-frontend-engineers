#!/usr/bin/env bash
# QuickVote を curl で操作する小さな関数群。jq が無い環境でも動くよう、JSON の取り出しは python を使う。
#   source scripts/qv.sh
#   qv_session  alice            … Cookie の入れ物 /tmp/qv-alice.jar を作る（BASE の / を叩いて XSRF-TOKEN を受け取る）
#   qv_post     alice  URL  JSON … CSRF ヘッダー付きで POST（Cookie は更新される）
#   qv_put      alice  URL  JSON
#   qv_get      alice  URL
#   json        'd["id"]'        … 標準入力の JSON から値を取り出す
BASE="${BASE:-http://localhost:18038}"
JAR_DIR="${JAR_DIR:-/tmp}"

json() { python -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
_jar() { echo "$JAR_DIR/qv-$1.jar"; }
_tok() { awk '$6=="XSRF-TOKEN"{print $7}' "$(_jar "$1")" | tail -1; }

qv_session() { rm -f "$(_jar "$1")"; curl -s -o /dev/null -c "$(_jar "$1")" "$BASE/"; }
qv_get()  { curl -s -b "$(_jar "$1")" -c "$(_jar "$1")" "${3:-$BASE}$2"; }
qv_post() { curl -s -b "$(_jar "$1")" -c "$(_jar "$1")" -H "X-XSRF-TOKEN: $(_tok "$1")" -H 'Content-Type: application/json' -X POST -d "$3" "${4:-$BASE}$2"; }
qv_put()  { curl -s -b "$(_jar "$1")" -c "$(_jar "$1")" -H "X-XSRF-TOKEN: $(_tok "$1")" -H 'Content-Type: application/json' -X PUT  -d "$3" "${4:-$BASE}$2"; }
# 戻り値の形:  qv_post alice /api/polls '{...}'  → 作成した投票の JSON
