#!/bin/sh
# 第27章：応答が消えるとき。リファレンス実装の jar を動かした状態で実行する。
# 事前: docker compose up -d、jar を --server.port=18027 で起動（README 参照）。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
. "$(dirname "$0")/common.sh"
rm -f "$JAR"
$PSQL -q -c "TRUNCATE polls, visitors RESTART IDENTITY CASCADE"
curl -s -c "$JAR" -o /dev/null "$BASE/"            # XSRF-TOKEN を受け取る

echo "== 投票を作る"
POLL=$(api POST /api/polls '{"title":"ランチ","closesInMinutes":60,"allowMemberOptions":false,"options":["カレー"]}' | sed 's/.*"id":"\([^"]*\)".*/\1/')
echo "pollId=$POLL"

echo "== 選択肢の保存を終えるまでに3秒かかるようにする（トリガーで pg_sleep）"
$PSQL -q <<'SQL'
CREATE OR REPLACE FUNCTION lab_slow() RETURNS trigger AS $$ BEGIN PERFORM pg_sleep(3); RETURN NEW; END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS lab_slow_trg ON options;
CREATE TRIGGER lab_slow_trg AFTER INSERT ON options FOR EACH ROW EXECUTE FUNCTION lab_slow();
SQL

echo "== A. 伝票番号を毎回つけ替えるクライアント（＝キーがないのと同じ）"
api POST "/api/polls/$POLL/options" '{"label":"うどん"}' -H "Idempotency-Key: attempt-1" --max-time 1
echo "  curl の終了コード=$?（28 = タイムアウト）"
sleep 4   # サーバー側の処理は続いていて、そのうちコミットされる
echo "-- クライアントは失敗したと思って、新しいキーで再送"
api POST "/api/polls/$POLL/options" '{"label":"うどん"}' -H "Idempotency-Key: attempt-2" -o /dev/null -w '  HTTP %{http_code}\n'
$PSQL -c "SELECT id, label FROM options ORDER BY id"

echo "== B. 同じ伝票番号で再送するクライアント"
api POST "/api/polls/$POLL/options" '{"label":"そば"}' -H "Idempotency-Key: order-0001" --max-time 1
echo "  curl の終了コード=$?（28 = タイムアウト）"
sleep 4
echo "-- 同じキーで再送"
api POST "/api/polls/$POLL/options" '{"label":"そば"}' -H "Idempotency-Key: order-0001" -i | grep -iE '^HTTP|^Idempotent-Replayed|^\{'
$PSQL -c "SELECT id, label FROM options ORDER BY id"

echo "== C. 同じキーで中身が違う"
api POST "/api/polls/$POLL/options" '{"label":"ラーメン"}' -H "Idempotency-Key: order-0001" -w '\n  HTTP %{http_code}\n'

echo "== 冪等性の記録"
$PSQL -c "SELECT visitor_id, idem_key, left(request_hash,12) AS hash, status, response_body, created_at FROM idempotency_records ORDER BY created_at"

$PSQL -q -c "DROP TRIGGER lab_slow_trg ON options"
