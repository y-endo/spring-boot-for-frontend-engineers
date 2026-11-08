#!/usr/bin/env bash
# nginx（ラウンドロビン）の前で、bob の画面に何本届くか。alice は6回、投票を切り替える。
cd "$(dirname "$0")/.."; source scripts/qv.sh
BASE=http://localhost:18038
qv_session alice
PID=$(qv_post alice /api/polls '{"title":"Lunch","closesInMinutes":60,"allowMemberOptions":true,"options":["Curry","Ramen"]}' | json 'd["id"]')
D=$(qv_get alice /api/polls/$PID)
CODE=$(echo "$D" | json 'd["joinCode"]')
O1=$(echo "$D" | json 'd["options"][0]["id"]'); O2=$(echo "$D" | json 'd["options"][1]["id"]')
qv_session bob; qv_post bob /api/join "{\"code\":\"$CODE\"}" >/dev/null

T0=$(date +%s)
( curl -sS -N --max-time 14 -b "$(_jar bob)" $BASE/api/polls/$PID/events | grep --line-buffered '^id:'    | while IFS= read -r l; do echo "$(( $(date +%s)-T0 ))s $l"; done > /tmp/rr-bob.txt ) &
sleep 3
for i in 1 2 3 4 5 6; do
  if [ $((i % 2)) = 1 ]; then opt=$O1; else opt=$O2; fi; qv_put alice /api/polls/$PID/my-vote "{\"optionId\":$opt}" >/dev/null; echo "$(( $(date +%s)-T0 ))s alice の更新 $i"; sleep 1
done
wait
echo "alice が行った更新: 6 回 / bob の画面に届いたイベント: $(wc -l < /tmp/rr-bob.txt) 件"; cat /tmp/rr-bob.txt | tr '\n' ' '; echo
