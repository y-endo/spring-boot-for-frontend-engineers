DROP INDEX IF EXISTS participants_visitor_poll_idx;
ANALYZE;
\echo === 1. 訪問者 500 が参加している投票（participants_visitor_idx あり）
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) SELECT poll_id FROM participants WHERE visitor_id = 500 AND revoked_at IS NULL;

\echo === 2. participants_visitor_idx を消すと（UNIQUE (poll_id, visitor_id) は先頭が poll_id なので、visitor_id だけの検索には向かない）
DROP INDEX participants_visitor_idx;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) SELECT poll_id FROM participants WHERE visitor_id = 500 AND revoked_at IS NULL;

\echo === 3. 逆に poll_id だけの検索は、UNIQUE (poll_id, visitor_id) の先頭列で足りる
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) SELECT visitor_id FROM participants WHERE poll_id = 100;

\echo === 4. 両方を指定する検索（本線の findByPollAndVisitor / findActive）
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) SELECT id, role FROM participants WHERE poll_id = 100 AND visitor_id = 1000100 AND revoked_at IS NULL;

\echo === 5. 列順の違い。participants のコピー（poll_id, visitor_id の2列だけ）に、列順の違うインデックスを作る
CREATE TEMP TABLE pv AS SELECT poll_id, visitor_id FROM participants;
CREATE INDEX pv_visitor_poll ON pv (visitor_id, poll_id);
ANALYZE pv;
\echo --- (visitor_id, poll_id) のインデックスで、poll_id だけを検索する
EXPLAIN (ANALYZE, COSTS OFF, BUFFERS) SELECT visitor_id FROM pv WHERE poll_id = 100;
DROP INDEX pv_visitor_poll;
CREATE INDEX pv_poll_visitor ON pv (poll_id, visitor_id);
ANALYZE pv;
\echo --- (poll_id, visitor_id) のインデックスで、同じ検索
EXPLAIN (ANALYZE, COSTS OFF, BUFFERS) SELECT visitor_id FROM pv WHERE poll_id = 100;

\echo === 6. 後片付け: 消したインデックスを戻す
CREATE INDEX participants_visitor_idx ON participants (visitor_id);
