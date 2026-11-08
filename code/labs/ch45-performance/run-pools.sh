#!/bin/sh
# プールの大きさを変えて、同じ負荷試験（k6）を繰り返す。使い方: sh run-pools.sh 3 10 30
COMPOSE="docker compose -p qv45 -f ../../quickvote/compose.app.yaml -f compose.lab.yaml"
for POOL in "$@"; do
  POOL=$POOL $COMPOSE up -d > /dev/null 2>&1
  i=0
  until [ "$(docker inspect -f '{{.State.Health.Status}}' qv45-app-1)" = healthy ]; do
    sleep 2; i=$((i+1))
    if [ $i -gt 60 ] || [ "$(docker inspect -f '{{.State.Running}}' qv45-app-1)" != true ]; then echo "pool=$POOL: 起動に失敗"; break; fi
  done
  [ "$(docker inspect -f '{{.State.Health.Status}}' qv45-app-1)" = healthy ] || continue
  echo "##### pool=$POOL"
  sh run-k6.sh qv45 49 30s > observed-k6-pool$POOL.txt 2>&1
  grep -E "kind:|http_req_failed\.|http_reqs|sample|✗" observed-k6-pool$POOL.txt | cut -c1-160
done
