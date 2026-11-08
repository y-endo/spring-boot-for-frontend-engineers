#!/usr/bin/env bash
# 参加コードの総当たり対策（訪問者ごとに「容量10・毎分10」）が、2台でどう緩むか。
# 同じ訪問者(Cookie)が、存在しないコードで POST /api/join を連打する。
cd "$(dirname "$0")/.."; source scripts/qv.sh
APP1=http://localhost:18138; APP2=http://localhost:18238
qv_session eve; qv_post eve /api/join '{"code":"AAAAAAAA"}' $APP1 >/dev/null     # 訪問者Cookieをもらう（これが1回目）
attempt() { # $1=台 $2=URL
  codes=""; for i in $(seq 1 12); do
    c=$(curl -s -o /dev/null -w '%{http_code}' -b "$(_jar eve)" -c "$(_jar eve)" -H "X-XSRF-TOKEN: $(_tok eve)" -H 'Content-Type: application/json' -d '{"code":"AAAAAAAA"}' $2/api/join)
    codes="$codes $c"; done; echo "$1: $codes"; }
attempt "app1 に12回" $APP1
attempt "app2 に12回" $APP2
attempt "app1 にもう12回" $APP1
