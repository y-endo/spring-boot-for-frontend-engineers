\timing off
ANALYZE;
-- 深いページ（20,001 件目から 20 件）。インデックスなし
EXPLAIN (ANALYZE, BUFFERS) SELECT id, title, created_at FROM polls ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 20000;

CREATE INDEX polls_created_at_id_idx ON polls (created_at DESC, id DESC);
ANALYZE polls;
-- 同じクエリ。インデックスあり（OFFSET は「読んで捨てる」ので、深いほど遅い）
EXPLAIN (ANALYZE, BUFFERS) SELECT id, title, created_at FROM polls ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 20000;
-- 1 ページ目
EXPLAIN (ANALYZE, BUFFERS) SELECT id, title, created_at FROM polls ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0;

-- キーセット: 「前のページの最後の行より後ろ」から 20 件。20,000 件目の行を取得
SELECT created_at AS last_created_at, id AS last_id FROM polls ORDER BY created_at DESC, id DESC OFFSET 19999 LIMIT 1 \gset
\echo 前のページの最後: :last_id
EXPLAIN (ANALYZE, BUFFERS) SELECT id, title, created_at FROM polls
WHERE (created_at, id) < (:'last_created_at', :last_id)
ORDER BY created_at DESC, id DESC LIMIT 20;
