#!/bin/sh
# QuickVote を小さなメモリ制限で起動して、起動できるか・メモリをどれだけ使うかを見る。
# 前提: qv42 の compose が起動済み（postgres に qv42_default ネットワークで届く）。
for MEM in 256m 384m 512m; do
  docker rm -f qv42-mem >/dev/null 2>&1
  docker run -d --name qv42-mem --network qv42_default -m $MEM -p 18043:8080 \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/quickvote \
    -e SPRING_DATASOURCE_USERNAME=quickvote -e SPRING_DATASOURCE_PASSWORD=quickvote \
    -e QUICKVOTE_VISITORCOOKIE_SECURE=false qv42-app >/dev/null
  # 最大90秒、起動（Started ...）か終了を待つ
  i=0
  while [ $i -lt 90 ]; do
    if docker logs qv42-mem 2>&1 | grep -q "Started QuickvoteApplication"; then STATE=started; break; fi
    if [ "$(docker inspect -f '{{.State.Running}}' qv42-mem)" != true ]; then STATE=exited; break; fi
    i=$((i+1)); sleep 1
  done
  echo "=== -m $MEM : ${STATE:-timeout} ($i s)"
  docker logs qv42-mem 2>&1 | grep -o 'Started QuickvoteApplication in [0-9.]* seconds'
  docker inspect -f 'OOMKilled={{.State.OOMKilled}} ExitCode={{.State.ExitCode}}' qv42-mem
  docker logs qv42-mem 2>&1 | grep -E "OutOfMemory" | head -2 | cut -c1-200
  if [ "$STATE" = started ]; then
    sleep 3; docker stats --no-stream --format 'mem={{.MemUsage}}' qv42-mem
  fi
done
docker rm -f qv42-mem >/dev/null 2>&1
