#!/bin/sh
# 最後の LAB-MARK 以降の、サーバーが受け取った SQL から BEGIN / COMMIT / ROLLBACK / INSERT / SELECT ... の先頭行だけを出す。
# 使い方: docker exec part04-lab-pg psql -U lab -d lab -qc "SELECT 'LAB-MARK'"  → アプリ実行 → ./scripts/pgtx.sh
docker logs part04-lab-pg 2>&1 \
 | awk '/LAB-MARK/{n=NR} {a[NR]=$0} END{for(i=n+1;i<=NR;i++)print a[i]}' \
 | grep -E "^[0-9-]+ [0-9:.]+ UTC \[[0-9]+\] LOG:  (statement|execute)" \
 | sed -E 's/^[0-9-]+ ([0-9:.]+) UTC \[([0-9]+)\] LOG:  (statement|execute [^:]*): /\1 [\2] /' \
 | grep -E "\] (BEGIN|COMMIT|ROLLBACK|INSERT INTO [a-z_]+|SELECT 1|SET TRANSACTION|SELECT pg_|UPDATE [a-z_]+|SELECT .* FOR UPDATE)" 
