#!/bin/sh
# graceful shutdown の観察。使い方: sh shutdown.sh <compose プロジェクト> <BASE URL>
#  実験1: SSE（終わらないリクエスト）が1本つながっている状態で docker stop
#  実験2: 処理中のリクエスト（投票の行ロックを待っている参加）があるときに docker stop → 終わるまで待つか
PROJECT=${1:-qv45}; BASE=${2:-http://localhost:18045}
. ./lib.sh
mkdir -p .tmp
pg() { docker exec ${PROJECT}-postgres-1 psql -U quickvote -d quickvote "$@"; }
echo '{"title":"S","closesInMinutes":60,"options":["x"]}' > .tmp/poll.json
J=.tmp/h.jar; new_visitor $J; api $J POST /api/polls .tmp/poll.json | cat > .tmp/p.json
PID=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['id'])")
CODE=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['joinCode'])")

echo "=== 実験1: SSE が1本つながっている"
( curl -s -N -b $J --max-time 120 "$BASE/api/polls/$PID/events" > .tmp/sse.txt 2>&1 ) &
sleep 3; echo "SSE 受信済み: $(wc -c < .tmp/sse.txt) byte"
T0=$(date +%s%3N); docker stop ${PROJECT}-app-1 > /dev/null; T1=$(date +%s%3N)
echo "docker stop にかかった時間: $((T1 - T0)) ms  終了コード: $(docker inspect -f '{{.State.ExitCode}}' ${PROJECT}-app-1)"
docker logs ${PROJECT}-app-1 2>&1 | grep -E "graceful|Graceful|Commencing|Shutdown|shutdown" | python -c "
import sys,json
for l in sys.stdin:
    try: d=json.loads(l); print(d['@timestamp'][11:23], d['log']['level'], d['message'][:110])
    except Exception: print(l[:120])"
docker start ${PROJECT}-app-1 > /dev/null
until [ "$(docker inspect -f '{{.State.Health.Status}}' ${PROJECT}-app-1)" = healthy ]; do sleep 2; done

echo "=== 実験2: 処理中のリクエスト（ロック待ちの参加）があるときの停止"
( pg -c "BEGIN; SELECT id FROM polls WHERE join_code = '$CODE' FOR UPDATE; SELECT pg_sleep(12); COMMIT;" > /dev/null 2>&1 ) &
sleep 1
echo "{\"code\":\"$CODE\"}" > .tmp/join.json
V=.tmp/v.jar; new_visitor $V
( api $V POST /api/join .tmp/join.json -o /dev/null -w "進行中だった参加リクエストの結果: HTTP %{http_code}（%{time_total}秒）\n" ) &
sleep 2
echo "(+3s) docker stop を実行。ロックはあと約10秒で解放される"
T0=$(date +%s%3N); docker stop ${PROJECT}-app-1 > /dev/null; T1=$(date +%s%3N)
echo "docker stop にかかった時間: $((T1 - T0)) ms  終了コード: $(docker inspect -f '{{.State.ExitCode}}' ${PROJECT}-app-1)"
wait
docker logs ${PROJECT}-app-1 2>&1 | grep -E "graceful|Graceful|Commencing" | python -c "
import sys,json
for l in sys.stdin:
    d=json.loads(l); print(d['@timestamp'][11:23], d['log']['level'], d['message'][:110])"
docker start ${PROJECT}-app-1 > /dev/null
rm -rf .tmp
