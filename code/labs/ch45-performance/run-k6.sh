#!/bin/sh
# k6 を Docker で実行する。使い方: sh run-k6.sh <compose プロジェクト名> [VU数] [実行時間]
#   例: sh run-k6.sh qv45 49 30s
# 実行中の HikariCP の状態（active / pending）と CPU も5秒ごとに記録する。
PROJECT=${1:-qv45}; VUS=${2:-49}; DURATION=${3:-30s}
( for i in 1 2 3 4 5 6; do
    sleep 5
    a=$(docker exec ${PROJECT}-app-1 curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.active | python metric.py | sed 's/.*VALUE=//')
    p=$(docker exec ${PROJECT}-app-1 curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.pending | python metric.py | sed 's/.*VALUE=//')
    c=$(docker stats --no-stream --format '{{.CPUPerc}}' ${PROJECT}-app-1)
    echo "sample +${i}x5s  hikari active=$a pending=$p  app cpu=$c"
  done ) > .samples &
MSYS_NO_PATHCONV=1 docker run --rm -i --network "${PROJECT}_default" \
  -e BASE=http://app:8080 -e VUS="$VUS" -e DURATION="$DURATION" \
  -v "$(pwd -W 2>/dev/null || pwd):/scripts" grafana/k6 run --quiet --no-color /scripts/vote.js
wait; cat .samples; rm -f .samples
