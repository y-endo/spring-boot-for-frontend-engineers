#!/bin/sh
# 「ロックを握ったままのトランザクション」を作り、そのあいだ API がどうなるかを測る。
#   使い方: sh lock-hold.sh <compose プロジェクト> <BASE URL> [ロックを握る秒数]
#   例:     sh lock-hold.sh qv45 http://localhost:18045 40
# 1. 投票A（ロックされる）と投票B（無関係）を作る
# 2. psql で投票A の行を SELECT ... FOR UPDATE して、HOLD 秒握る
# 3. 25人が同時に投票A へ参加（POST /api/join）。参加は投票A の行ロックを取るので、全員が待たされる
# 4. 5秒後、無関係な投票B の詳細を取得して、かかった時間を測る
PROJECT=${1:-qv45}; BASE=${2:-http://localhost:18045}; HOLD=${3:-40}
. ./lib.sh
mkdir -p .tmp
rm -f .tmp/*
pg() { docker exec ${PROJECT}-postgres-1 psql -U quickvote -d quickvote "$@"; }
mgmt() { docker exec ${PROJECT}-app-1 curl -s "http://localhost:9090/actuator/metrics/$1" | python metric.py | sed 's/.*=//'; }

echo '{"title":"A","closesInMinutes":60,"options":["x"]}' > .tmp/poll.json
JA=.tmp/hostA.jar; JB=.tmp/hostB.jar
new_visitor $JA; api $JA POST /api/polls .tmp/poll.json | cat > .tmp/a.json
new_visitor $JB; api $JB POST /api/polls .tmp/poll.json | cat > .tmp/b.json
CODE=$(python -c "import json;print(json.load(open('.tmp/a.json',encoding='utf-8'))['joinCode'])")
BID=$(python -c "import json;print(json.load(open('.tmp/b.json',encoding='utf-8'))['id'])")

echo "--- 投票A の行を ${HOLD} 秒ロックする"
( pg -c "BEGIN; SELECT id FROM polls WHERE join_code = '$CODE' FOR UPDATE; SELECT pg_sleep($HOLD); COMMIT;" > /dev/null 2>&1 ) &
sleep 2
T0=$(date +%s)
echo "--- 25人が同時に参加（投票A）"
echo "{\"code\":\"$CODE\"}" > .tmp/join.json
FMT='join %{http_code} %{time_total}'
for i in $(seq 1 25); do
  ( J=.tmp/v$i.jar; new_visitor $J
    api $J POST /api/join .tmp/join.json -o /dev/null -w "$FMT" --max-time $((HOLD + 40)) > .tmp/r$i.txt 2>&1
    echo >> .tmp/r$i.txt ) &
done
sleep 5
echo "--- +5s: HikariCP  active=$(mgmt hikaricp.connections.active)  pending=$(mgmt hikaricp.connections.pending)"
api $JB GET /api/polls/$BID "" -o /dev/null -w "無関係な投票B の詳細: HTTP %{http_code}  %{time_total} 秒" --max-time $((HOLD + 40))
echo
echo "--- ($(( $(date +%s) - T0 )) 秒経過) HikariCP  active=$(mgmt hikaricp.connections.active)  pending=$(mgmt hikaricp.connections.pending)  timeout(累計)=$(mgmt hikaricp.connections.timeout)"
wait
echo "--- 参加リクエスト 25本の結果"
cat .tmp/r*.txt | grep join > .tmp/times.txt
awk '{n[$2]++; if(!($2 in lo)||$3+0<lo[$2]+0)lo[$2]=$3; if($3+0>hi[$2]+0)hi[$2]=$3} END{for(k in n) printf("HTTP %s: %d 本（%.1f 〜 %.1f 秒）\n", k, n[k], lo[k], hi[k])}' .tmp/times.txt
rm -rf .tmp
