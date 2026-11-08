#!/bin/sh
# 第29章：2台が同時に削除処理を始めたら。アドバイザリロックで1台だけが実行し、もう1台はスキップする。
# リポジトリのルートで実行する: sh code/labs/ch29-scheduling/two-instances.sh
. "$(dirname "$0")/common.sh"
sh "$(dirname "$0")/seed.sh" 100

echo "== 1行ごとに0.05秒かかる削除（重い削除の模型）"
$PSQL -q <<'SQL'
CREATE OR REPLACE FUNCTION lab_slow_delete() RETURNS trigger AS $$ BEGIN PERFORM pg_sleep(0.05); RETURN OLD; END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS lab_slow_delete_trg ON polls;
CREATE TRIGGER lab_slow_delete_trg BEFORE DELETE ON polls FOR EACH ROW EXECUTE FUNCTION lab_slow_delete();
SQL

echo "== 同じ DB に2台をほぼ同時に起動（どちらも起動直後に削除処理が走る）"
start_app 18029 /tmp/ch29-a.log --quickvote.cleanup.interval=30s
A=$!
start_app 18129 /tmp/ch29-b.log --quickvote.cleanup.interval=30s
B=$!

# 削除の最中（ロックを持っている間）に pg_locks を見る
for i in $(seq 1 40); do
  n=$($PSQL -t -A -c "SELECT count(*) FROM pg_locks WHERE locktype='advisory'")
  if [ "$n" -gt 0 ]; then
    echo "== 削除の最中の pg_locks（advisory）"
    $PSQL -c "SELECT l.locktype, l.objid AS lock_key, l.granted, a.pid, a.state, left(a.query, 40) AS query FROM pg_locks l JOIN pg_stat_activity a USING (pid) WHERE l.locktype='advisory'"
    break
  fi
  sleep 1
done
sleep 15
kill $A $B 2>/dev/null
$PSQL -q -c "DROP TRIGGER lab_slow_delete_trg ON polls"
echo "== インスタンスA のログ"; cleaner_log /tmp/ch29-a.log | head -5
echo "== インスタンスB のログ"; cleaner_log /tmp/ch29-b.log | head -5
$PSQL -t -A -c "SELECT 'polls の残り: ' || count(*) FROM polls"
