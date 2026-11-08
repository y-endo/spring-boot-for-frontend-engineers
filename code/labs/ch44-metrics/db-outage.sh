#!/bin/sh
# DB を止めて、readiness / liveness / API / コンテナの health がどう変わるかを時系列で観察する。
# 前提: ../ch42-docker の compose（qv42）が起動済み。管理ポート 9090 には、コンテナの中から問い合わせる。
# COOKIE は有効な訪問者の Cookie ファイル（curl の -b に渡す）。API は DB を引く GET /api/polls/{id}。
COOKIE=${COOKIE:-jar.txt}
BASE=${BASE:-http://localhost:18042}
one() {  # one <名前> <curlの引数...> → "名前=ステータス(秒)"
  name=$1; shift
  out=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 60 "$@")
  echo "$name=${out% *}(${out#* }s)"
}
probe() {
  ( r=$(docker exec qv42-app-1 curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 60 http://localhost:9090/actuator/health/readiness)
    echo "readiness=${r% *}(${r#* }s)" > .p1 ) &
  ( l=$(docker exec qv42-app-1 curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 60 http://localhost:9090/actuator/health/liveness)
    echo "liveness=${l% *}(${l#* }s)" > .p2 ) &
  ( one "API" -b "$COOKIE" "$BASE/api/polls/00000000-0000-0000-0000-000000000000" > .p3 ) &
  wait
  h=$(docker inspect -f '{{.State.Health.Status}}' qv42-app-1)
  echo "t=+$(( $(date +%s) - T0 ))s  $(cat .p1) $(cat .p2) $(cat .p3) container=$h"
}
T0=$(date +%s)
echo "--- DB 稼働中"; probe
echo "--- DB を停止"; docker stop qv42-postgres-1 >/dev/null
for i in 1 2 3; do probe; done
echo "--- DB を起動"; docker start qv42-postgres-1 >/dev/null
for i in 1 2 3 4; do probe; sleep 2; done
rm -f .p1 .p2 .p3
