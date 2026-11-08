#!/usr/bin/env bash
# 「隣のサーバーにつないだ人に届くか」を確かめる。
#   ./scripts/neighbor.sh
# 手順: alice が app1 で投票を作る → bob が app2 経由で参加 →
#       bob は app1 の SSE につなぐ → alice が app2 / app1 から投票を切り替える → bob の画面に何が届くか
cd "$(dirname "$0")/.."; source scripts/qv.sh
APP1=http://localhost:18138; APP2=http://localhost:18238

qv_session alice
P=$(qv_post alice /api/polls '{"title":"Lunch","closesInMinutes":60,"allowMemberOptions":true,"options":["Curry","Ramen"]}' $APP1)
PID=$(echo "$P" | json 'd["id"]')
D=$(qv_get alice /api/polls/$PID $APP1)
CODE=$(echo "$D" | json 'd["joinCode"]')
O1=$(echo "$D" | json 'd["options"][0]["id"]'); O2=$(echo "$D" | json 'd["options"][1]["id"]')
# bob は「app2 に」参加する。alice が app1 で作った投票に、別の台から参加できる（状態はDBにあるので）
qv_session bob; qv_post bob /api/join "{\"code\":\"$CODE\"}" $APP2 | json '"bob が app2 経由で参加: " + d["role"]'

T0=$(date +%s)
# bob は app1 の SSE につなぐ
( curl -sS -N --max-time 14 -b "$(_jar bob)" $APP1/api/polls/$PID/events | grep --line-buffered -E '^(id|event|data):' \
   | while IFS= read -r l; do echo "  [bob@app1 $(( $(date +%s)-T0 ))s] $l"; done ) &
sleep 3
echo "--- alice が app2 から投票 (Curry)"; qv_put alice /api/polls/$PID/my-vote "{\"optionId\":$O1}" $APP2 >/dev/null; sleep 3
echo "--- alice が app2 から投票 (Ramen)"; qv_put alice /api/polls/$PID/my-vote "{\"optionId\":$O2}" $APP2 >/dev/null; sleep 3
echo "--- alice が app1 から投票 (Curry)"; qv_put alice /api/polls/$PID/my-vote "{\"optionId\":$O1}" $APP1 >/dev/null; sleep 3
wait
echo "--- DB に残っているイベント"; docker exec ch38-multi-pg-1 psql -U quickvote -At -c "SELECT seq, type, payload FROM poll_events WHERE poll_id=(SELECT id FROM polls ORDER BY id DESC LIMIT 1) ORDER BY seq"
