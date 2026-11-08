#!/bin/sh
# DB を止めて 15 本の API を同時に投げ、HikariCP の指標がどう動くかを5秒ごとに読む。
# 前提: ../ch42-docker の compose（qv42）が起動済み。COOKIE は有効な訪問者の Cookie ファイル。
COOKIE=${COOKIE:-jar.txt}
BASE=${BASE:-http://localhost:18042}
val() { docker exec qv42-app-1 curl -s "http://localhost:9090/actuator/metrics/$1" | python metric.py | sed 's/.*VALUE=//; s/.*COUNT=\([0-9.]*\).*/\1/'; }
snap() { echo "t=+$(( $(date +%s) - T0 ))s  active=$(val hikaricp.connections.active) pending=$(val hikaricp.connections.pending) timeout(累計)=$(val hikaricp.connections.timeout)"; }
T0=$(date +%s)
echo "--- 平常時"; snap
docker stop qv42-postgres-1 >/dev/null
echo "--- DB 停止。同時に 15 本の API を投げる"
for i in $(seq 1 15); do
  ( curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" -b "$COOKIE" --max-time 60 "$BASE/api/polls/00000000-0000-0000-0000-000000000000" >> .r ) &
done
sleep 3; snap; sleep 12; snap; sleep 18; snap
wait
echo "--- 応答（15本）:"; sort .r | uniq -c | sort -rn | head -3; rm -f .r
docker start qv42-postgres-1 >/dev/null
sleep 12; echo "--- DB 起動後"; snap
echo "500 の件数: $(docker exec qv42-app-1 curl -s 'http://localhost:9090/actuator/metrics/http.server.requests?tag=status:500' | python metric.py)"
