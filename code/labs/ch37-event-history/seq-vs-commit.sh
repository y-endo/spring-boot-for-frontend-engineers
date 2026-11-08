#!/usr/bin/env bash
# 「採番の順 ≠ コミットの順」を2つのトランザクションで再現する。
#   ./seq-vs-commit.sh sequence   … シーケンスで採番（取りこぼしが起きる）
#   ./seq-vs-commit.sh rowlock    … 親行の UPDATE で採番（取りこぼしが起きない）
set -euo pipefail
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
PSQL="docker exec -i -e PGOPTIONS=--client-min-messages=warning ch37-lab-pg-1 psql -U lab -d lab -q -X -v ON_ERROR_STOP=1"
MODE="${1:-sequence}"

$PSQL <<'SQL'
DROP TABLE IF EXISTS ev; DROP SEQUENCE IF EXISTS ev_seq; DROP TABLE IF EXISTS poll;
CREATE SEQUENCE ev_seq;
CREATE TABLE poll (id int PRIMARY KEY, last_seq bigint NOT NULL);
INSERT INTO poll VALUES (1, 0);
CREATE TABLE ev (seq bigint PRIMARY KEY, who text);
SQL

if [ "$MODE" = sequence ]; then
  TAKE="SELECT nextval('ev_seq') AS seq \gset"
else
  TAKE="UPDATE poll SET last_seq = last_seq + 1 WHERE id = 1 RETURNING last_seq AS seq \gset"
fi
T0=$(date +%s)
log() { echo "$(( $(date +%s) - T0 ))s  $*"; }

# トランザクションA: 先に採番し、3秒かけてコミットする（遅いトランザクション）
( echo "BEGIN; $TAKE"; echo "INSERT INTO ev VALUES (:seq, 'A'); SELECT pg_sleep(3); COMMIT;" ) | $PSQL -o /dev/null &
sleep 1
# トランザクションB: 後から採番し、すぐコミットする
( echo "BEGIN; $TAKE"; echo "INSERT INTO ev VALUES (:seq, 'B'); COMMIT;" ) | $PSQL &

# 読み手: 1秒ごとに「最後に見たseqより後」を読む（Last-Event-ID と同じやり方）
last=0
for i in 1 2 3 4 5 6; do
  sleep 1
  rows=$($PSQL -At -c "SELECT seq || ':' || who FROM ev WHERE seq > $last ORDER BY seq" | tr '\n' ' ')
  log "読み手: seq > $last → [${rows}]"
  for r in $rows; do last=${r%%:*}; done
done
wait
echo "--- 最終的にテーブルにあるもの"; $PSQL -c "SELECT seq, who FROM ev ORDER BY seq"
