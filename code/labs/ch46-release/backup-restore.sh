#!/bin/sh
# バックアップ（pg_dump）と復元（pg_restore）を実際に行い、復元したDBでアプリが動くことまで確かめる。
#   前提: qv46 の compose が起動済み（元のDB: qv46-postgres-1、アプリ: http://localhost:18046）
#   復元先: 別のコンテナ qv46-restore（ポート 25444）、復元後のアプリ: qv46-restored-app（ポート 18044）
BASE=${BASE:-http://localhost:18046}
. ./lib.sh
# Git Bash は /tmp/... のような引数を Windows のパスに書き換えるので、docker だけ変換を止める
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
src() { docker exec qv46-postgres-1 psql -U quickvote -d quickvote -tA "$@"; }
dst() { docker exec qv46-restore psql -U quickvote -d quickvote -tA "$@"; }
counts='SELECT (SELECT count(*) FROM polls)||" polls / "||(SELECT count(*) FROM options)||" options / "||(SELECT count(*) FROM votes)||" votes / "||(SELECT count(*) FROM visitors)||" visitors / "||(SELECT count(*) FROM flyway_schema_history)||" migrations"'
counts=$(echo "$counts" | sed "s/\"/'/g")
mkdir -p .tmp backup

# 0. 復元後に確かめたい「本物の投票」を1つ作る（訪問者の Cookie ごと取っておく）
cat > .tmp/poll.json <<'JSON'
{"title":"復元テスト用の投票","closesInMinutes":10080,"allowMemberOptions":true,"options":["カレー","ラーメン"]}
JSON
new_visitor .tmp/host.jar; api .tmp/host.jar POST /api/polls .tmp/poll.json | cat > .tmp/p.json
PID=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['id'])" 2>/dev/null)
if [ -z "$PID" ]; then   # 直前に別の実験でDDLを流した直後は、最初の1本が失敗することがある（第46章）。もう一度
  new_visitor .tmp/host.jar; api .tmp/host.jar POST /api/polls .tmp/poll.json | cat > .tmp/p.json
  PID=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['id'])")
fi
OPT=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['options'][0]['id'])")
echo "{\"optionId\":$OPT}" > .tmp/vote.json
api .tmp/host.jar PUT /api/polls/$PID/my-vote .tmp/vote.json -o /dev/null -w "投票: HTTP %{http_code}
"

echo "### 1. バックアップ（pg_dump -Fc : カスタム形式。圧縮され、pg_restore で部分復元もできる）"
docker exec qv46-postgres-1 pg_dump -U quickvote -d quickvote -Fc -f /tmp/quickvote.dump
docker cp qv46-postgres-1:/tmp/quickvote.dump backup/quickvote.dump
ls -l backup/quickvote.dump | awk '{print "ダンプのサイズ: " $5 " byte"}'
echo "元のDB: $(src -c "$counts")"
echo "--- pg_restore --list の先頭（ダンプの目次）"
docker exec qv46-postgres-1 pg_restore --list /tmp/quickvote.dump | grep -E "TABLE DATA|TABLE public" | head -6

echo; echo "### 2. 復元先として、空のPostgreSQLを別に起動する"
docker rm -f qv46-restore > /dev/null 2>&1
docker run -d --name qv46-restore --network qv46_default -p 25444:5432 \
  -e POSTGRES_DB=quickvote -e POSTGRES_USER=quickvote -e POSTGRES_PASSWORD=quickvote postgres:18-alpine > /dev/null
until docker exec qv46-restore pg_isready -U quickvote -d quickvote > /dev/null 2>&1; do sleep 1; done
sleep 2
echo "復元前のテーブル数: $(dst -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")"

echo; echo "### 3. 復元（pg_restore）"
docker cp backup/quickvote.dump qv46-restore:/tmp/quickvote.dump
docker exec qv46-restore pg_restore -U quickvote -d quickvote --no-owner --exit-on-error /tmp/quickvote.dump && echo "pg_restore 終了コード 0"
echo "復元後: $(dst -c "$counts")"

echo; echo "### 4. 復元したDBでアプリを起動する（Flyway はマイグレーションを再実行しない）"
docker rm -f qv46-restored-app > /dev/null 2>&1
docker run -d --name qv46-restored-app --network qv46_default -p 18044:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://qv46-restore:5432/quickvote -e SPRING_DATASOURCE_USERNAME=quickvote \
  -e SPRING_DATASOURCE_PASSWORD=quickvote -e QUICKVOTE_VISITORCOOKIE_SECURE=false qv46-app > /dev/null
until docker exec qv46-restored-app curl -sf http://localhost:9090/actuator/health/readiness > /dev/null 2>&1; do sleep 2; done
docker logs qv46-restored-app 2>&1 | grep -E "Flyway|migrat" | python -c "
import sys,json
for l in sys.stdin:
    try: print(json.loads(l)['message'][:140])
    except Exception: pass"
echo "--- 元のアプリで作った投票を、元の Cookie のまま、復元したアプリで取得する"
BASE=http://localhost:18044
api .tmp/host.jar GET /api/polls/$PID "" -o .tmp/restored.json -w "HTTP %{http_code}\n"
python -c "import json;d=json.load(open('.tmp/restored.json',encoding='utf-8'));print('title =',d['title'],'/ role =',d['role'],'/ myVote =',d['myVoteOptionId'],'/ options =',[(o['label'],o['votes']) for o in d['options']])"

docker rm -f qv46-restored-app qv46-restore > /dev/null 2>&1
rm -rf .tmp backup
