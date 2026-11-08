#!/bin/sh
# 稼働中のアプリ（古いバージョン）の DB に、スキーマ変更を当てると何が起きるかを観察する。
#   前提: qv46 の compose が起動済み（BASE=http://localhost:18046）
BASE=${BASE:-http://localhost:18046}
. ./lib.sh
pg() { docker exec -i qv46-postgres-1 psql -U quickvote -d quickvote "$@"; }
mkdir -p .tmp
cat > .tmp/poll.json <<'JSON'
{"title":"ランチはどこにする？","closesInMinutes":60,"allowMemberOptions":true,"options":["カレー","ラーメン"]}
JSON
J=.tmp/host.jar; new_visitor $J
api $J POST /api/polls .tmp/poll.json | cat > .tmp/p.json
PID=$(python -c "import json;print(json.load(open('.tmp/p.json',encoding='utf-8'))['id'])")
status() {  # status <説明>  古いバージョンのアプリの API を2つ呼んで結果を表示
  d=$(api $J GET /api/polls/$PID "" -o /dev/null -w '%{http_code}')
  c=$(new_visitor .tmp/n.jar; api .tmp/n.jar POST /api/polls .tmp/poll.json -o /dev/null -w '%{http_code}')
  echo "$1 → 詳細の取得=$d  投票の作成=$c"
}
status "[0] 変更前                                  "

echo; echo "### A. 悪い例：稼働中に列の名前を変える（options.label → text）"
pg -c "ALTER TABLE options RENAME COLUMN label TO text"
status "[A] RENAME 直後（古いアプリが動いたまま）   "
pg -c "ALTER TABLE options RENAME COLUMN text TO label"
status "[A] 元に戻した後                            "

echo; echo "### B. expand：列を足すだけ（V7）。古いアプリは何も知らずに動く"
pg < migrations/V7__expand_add_polls_name.sql
status "[B] V7 適用後（name 列が増えた）            "
status "[B] もう一度                                "

echo; echo "### C. contract を早まる：全部のアプリが name を書く前に NOT NULL（V8）"
echo "(V7 のあとも古いアプリは name の入っていない行を作り続ける)"
pg -c "SELECT count(*) AS name_is_null FROM polls WHERE name IS NULL"
echo "--- そのまま V8 を当てると:"
pg -v ON_ERROR_STOP=1 < migrations/V8__contract_polls_name_not_null.sql
echo "--- 急いでもう一度バックフィルしてから V8 を当てると:"
pg -c "UPDATE polls SET name = title WHERE name IS NULL"
pg < migrations/V8__contract_polls_name_not_null.sql
status "[C] V8 適用後（古いアプリは name を書かない）"
pg -c "ALTER TABLE polls DROP CONSTRAINT polls_name_check" -c "ALTER TABLE polls ALTER COLUMN name DROP NOT NULL"
status "[C] V8 を取り消した後                       "
pg -c "ALTER TABLE polls DROP COLUMN name"
rm -rf .tmp
