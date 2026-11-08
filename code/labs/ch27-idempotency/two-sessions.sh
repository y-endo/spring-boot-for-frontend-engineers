#!/bin/sh
# 第27章：psql を2本使い、INSERT ... ON CONFLICT DO NOTHING が「先行のコミットまで待つ」ことを観察する。
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
. "$(dirname "$0")/common.sh"
$PSQL -q -c "DELETE FROM idempotency_records WHERE idem_key = 'psql-demo'"

INS="INSERT INTO idempotency_records (visitor_id, idem_key, request_hash, status, created_at) VALUES (1, 'psql-demo', repeat('a',64), 0, now()) ON CONFLICT (visitor_id, idem_key) DO NOTHING"

echo "--- セッションA：INSERT して、3秒コミットしない"
( $PSQL <<SQL
BEGIN;
$INS;
SELECT pg_sleep(3);
COMMIT;
SQL
) &
sleep 1
echo "--- セッションB：1秒後に同じキーで INSERT（ここで待たされる）"
$PSQL <<SQL
\timing on
$INS;
SQL
wait
