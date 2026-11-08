#!/bin/sh
# 期限切れ（delete_after が過去）の投票を N 件作る。最後の1件だけタイトルを 'poison' にする。
. "$(dirname "$0")/common.sh"
N="${1:-151}"
$PSQL -q -c "TRUNCATE polls, visitors RESTART IDENTITY CASCADE" 2>/dev/null
$PSQL -q <<SQL
INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
SELECT gen_random_uuid(),
       CASE WHEN g = $N THEN 'poison-秘密のタイトル' ELSE 'old-' || g END,
       'OPEN',
       translate(upper(substr(md5(g::text), 1, 8)), '01', 'GH'),
       false,
       now() - interval '8 days',
       now() - interval '2 days' + g * interval '1 second',   -- g が大きいほど「新しく期限切れになった」
       now() - interval '9 days'
FROM generate_series(1, $N) AS g;
SQL
$PSQL -c "SELECT count(*) AS expired_polls FROM polls WHERE delete_after <= now()"
