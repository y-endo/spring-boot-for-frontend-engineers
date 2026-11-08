#!/bin/sh
# 第29章：途中で失敗しても、消せたぶんは残り、直せば続きから消える。
# リポジトリのルート（D:\Workspace\web\java-study）で実行する: sh code/labs/ch29-scheduling/failure-resume.sh
. "$(dirname "$0")/common.sh"
sh "$(dirname "$0")/seed.sh" 151

echo "== 削除の途中で失敗するトリガー（タイトルが poison のとき、秘密を含むメッセージで失敗する）を仕込む"
$PSQL -q <<'SQL'
CREATE OR REPLACE FUNCTION lab_poison() RETURNS trigger AS $$
BEGIN
  IF OLD.title LIKE 'poison%' THEN
    RAISE EXCEPTION 'cannot delete poll titled %', OLD.title;
  END IF;
  RETURN OLD;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS lab_poison_trg ON polls;
CREATE TRIGGER lab_poison_trg BEFORE DELETE ON polls FOR EACH ROW EXECUTE FUNCTION lab_poison();
SQL

echo "== アプリを起動（10秒ごと・1バッチ50件・1回の実行は最大2バッチ）"
start_app 18029 /tmp/ch29-a.log --quickvote.cleanup.interval=10s --quickvote.cleanup.batch-size=50 --quickvote.cleanup.max-batches-per-run=2
APP=$!
sleep 22
echo "-- 1回目の実行後に残っている件数"
$PSQL -t -A -c "SELECT count(*) FROM polls"
sleep 12
echo "-- 2回目の実行後（poison を含むバッチは失敗する）"
$PSQL -t -A -c "SELECT count(*) FROM polls"

echo "== 原因を直す（トリガーを外す）"
$PSQL -q -c "DROP TRIGGER lab_poison_trg ON polls"
sleep 12
echo "-- 次の実行のあと"
$PSQL -t -A -c "SELECT count(*) FROM polls"

kill $APP 2>/dev/null
echo "== ログ（削除処理だけ）"
cleaner_log /tmp/ch29-a.log
echo "== 秘密のタイトルがログに出ていないか"
grep -c '秘密のタイトル' /tmp/ch29-a.log
