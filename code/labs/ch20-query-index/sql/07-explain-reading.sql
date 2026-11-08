\echo === 1. 統計がないテーブル（一時テーブルは autovacuum の対象外）。予想の行数と、実際の行数がずれる
CREATE TEMP TABLE pv AS SELECT poll_id, visitor_id FROM participants;
CREATE INDEX pv_poll_visitor ON pv (poll_id, visitor_id);
EXPLAIN (ANALYZE, BUFFERS) SELECT visitor_id FROM pv WHERE poll_id = 100;

\echo === 2. ANALYZE（統計の採取）のあと
ANALYZE pv;
EXPLAIN (ANALYZE, BUFFERS) SELECT visitor_id FROM pv WHERE poll_id = 100;

\echo === 3. 本線の findWithVoteCounts（LEFT JOIN + GROUP BY）。投票 100 の4選択肢
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.label, count(v.participant_id) AS votes
FROM options o LEFT JOIN votes v ON v.option_id = o.id
WHERE o.poll_id = 100 GROUP BY o.id, o.label ORDER BY o.id;

\echo === 4. 本線の findActive（公開IDと訪問者から参加者を探す）
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id AS poll_id, pt.id AS participant_id, pt.role
FROM polls p JOIN participants pt ON pt.poll_id = p.id
WHERE p.public_id = (SELECT public_id FROM polls WHERE id = 100) AND pt.visitor_id = 1000100 AND pt.revoked_at IS NULL;

\echo === 5. 期限切れ削除の対象を探す（delete_after のインデックス）。30日ぶんの投票を作ったので、期限を過ぎたものが多数ある
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM polls WHERE delete_after <= now() ORDER BY delete_after LIMIT 100;
