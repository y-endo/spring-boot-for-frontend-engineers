#!/bin/sh
# 第27章：PUT の投票は本質的に冪等／保持期限が切れたキーは新しい操作として扱われる。scenario.sh の後に実行する。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
. "$(dirname "$0")/common.sh"
POLL=$($PSQL -t -A -c "SELECT public_id FROM polls LIMIT 1")
OPT=$($PSQL -t -A -c "SELECT id FROM options ORDER BY id LIMIT 1")

echo "== PUT /my-vote を3回送る（同じ内容）"
for i in 1 2 3; do
  api PUT "/api/polls/$POLL/my-vote" "{\"optionId\":$OPT}" -o /dev/null -w "  HTTP %{http_code}\n"
done
$PSQL -c "SELECT count(*) AS votes_rows FROM votes" -c "SELECT seq, type FROM poll_events ORDER BY seq"

echo "== 保持期限（24時間）を過ぎたら？ 記録の作成時刻を25時間前にずらして、同じキーを再送する"
$PSQL -c "UPDATE idempotency_records SET created_at = created_at - interval '25 hours' WHERE idem_key = 'order-0001'"
api POST "/api/polls/$POLL/options" '{"label":"そば"}' -H "Idempotency-Key: order-0001" -i | grep -iE '^HTTP|^Idempotent-Replayed|^\{'
$PSQL -c "SELECT id, label FROM options ORDER BY id"
