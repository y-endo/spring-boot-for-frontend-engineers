#!/bin/sh
# 第27章：応答が返る前に再送したとき（同時に同じキー）。scenario.sh の後に実行する。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
. "$(dirname "$0")/common.sh"
POLL=$($PSQL -t -A -c "SELECT public_id FROM polls LIMIT 1")

$PSQL -q <<'SQL'
CREATE OR REPLACE FUNCTION lab_slow() RETURNS trigger AS $$ BEGIN PERFORM pg_sleep(3); RETURN NEW; END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS lab_slow_trg ON options;
CREATE TRIGGER lab_slow_trg AFTER INSERT ON options FOR EACH ROW EXECUTE FUNCTION lab_slow();
SQL

echo "== 1回目：1秒でタイムアウト（サーバーはまだ処理中）"
api POST "/api/polls/$POLL/options" '{"label":"うどん3"}' -H "Idempotency-Key: order-0003" --max-time 1
echo "  curl の終了コード=$?"

echo "== すぐ同じキーで再送（バックグラウンド）"
( api POST "/api/polls/$POLL/options" '{"label":"うどん3"}' -H "Idempotency-Key: order-0003" -i -w '\n  elapsed %{time_total}s\n' | grep -iE '^HTTP|^Idempotent-Replayed|^\{|elapsed' ) &
sleep 1
echo "== 再送の最中、DB の中では何が起きているか"
$PSQL -c "SELECT pid, state, wait_event_type, wait_event, left(query, 60) AS query FROM pg_stat_activity WHERE datname='quickvote' AND state <> 'idle' AND pid <> pg_backend_pid() ORDER BY query_start"
wait
$PSQL -c "SELECT id, label FROM options ORDER BY id"
$PSQL -q -c "DROP TRIGGER lab_slow_trg ON options"
