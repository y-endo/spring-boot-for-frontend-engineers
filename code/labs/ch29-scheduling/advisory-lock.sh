#!/bin/sh
# 第29章：アドバイザリロックを psql 2本で観察する。ロックは「接続」に紐づき、接続が切れれば自動で外れる。
. "$(dirname "$0")/common.sh"

echo "--- セッションA：ロックを取り、3秒後に接続を閉じる（unlock は呼ばない）"
( $PSQL -t -A <<'SQL'
SELECT 'A: pg_try_advisory_lock = ' || pg_try_advisory_lock(7001001);
SELECT pg_sleep(3) \g /dev/null
SQL
) &
sleep 1
echo "--- セッションB：1秒後に同じキーで試す（待たずに結果が返る）"
$PSQL -t -A -c "SELECT 'B: pg_try_advisory_lock = ' || pg_try_advisory_lock(7001001)"
wait
echo "--- Aの接続が閉じたあと"
$PSQL -t -A -c "SELECT 'B: pg_try_advisory_lock = ' || pg_try_advisory_lock(7001001)"
