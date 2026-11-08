#!/bin/sh
# 第28章：締切の判定は「ロックを取ったあと」の時刻で行う。リファレンス実装の jar を動かした状態で実行する。
# 事前: docker compose up -d、jar を --server.port=18028 で起動。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
. "$(dirname "$0")/common.sh"
rm -f "$JAR"
$PSQL -q -c "TRUNCATE polls, visitors RESTART IDENTITY CASCADE" 2>/dev/null
curl -s -c "$JAR" -o /dev/null "$BASE/"

echo "== 1. 締切は『何分後か』で送る。closesAt を計算するのはサーバー"
date -u +"クライアントの現在時刻（この端末）: %Y-%m-%dT%H:%M:%SZ"
api POST /api/polls '{"title":"ランチ","closesInMinutes":5,"allowMemberOptions":false,"options":["カレー","うどん"]}' | sed 's/.*"closesAt":"\([^"]*\)".*/サーバーが計算した closesAt:         \1/'; echo
echo "-- 範囲外（4分）は 400"
api POST /api/polls '{"title":"ランチ","closesInMinutes":4,"allowMemberOptions":false,"options":["カレー"]}' -w '\n  HTTP %{http_code}\n'

POLL=$($PSQL -t -A -c "SELECT public_id FROM polls ORDER BY id LIMIT 1")
OPT=$($PSQL -t -A -c "SELECT id FROM options ORDER BY id LIMIT 1")

echo "== 2. 締切を『今から2秒後』にして、投票が『締切前に届いて、締切後に処理される』状況を作る"
$PSQL -q -c "UPDATE polls SET closes_at = clock_timestamp() + interval '2 seconds' WHERE public_id = '$POLL'"
echo "-- 別のトランザクション（psql）が polls の行を4秒間ロックしている"
( $PSQL -q <<SQL
BEGIN;
SELECT id FROM polls WHERE public_id = '$POLL' FOR UPDATE;
SELECT pg_sleep(4);
COMMIT;
SQL
) >/dev/null &
sleep 0.5
echo "-- 締切の1.5秒前に投票リクエストが届く（行ロックで待たされる）"
START=$(date +%s.%N)
api PUT "/api/polls/$POLL/my-vote" "{\"optionId\":$OPT}" -w '\n  HTTP %{http_code}\n'
END=$(date +%s.%N)
echo "  応答まで $(echo "$END - $START" | bc 2>/dev/null || awk "BEGIN{print $END-$START}") 秒"
wait
$PSQL -c "SELECT count(*) AS votes FROM votes"
