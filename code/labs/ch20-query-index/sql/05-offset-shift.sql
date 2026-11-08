-- ページの途中で新しい投票が作られると、OFFSET 方式は同じ行を2回見せる（トランザクションの中で試し、最後に取り消す）
BEGIN;
SELECT 'ページ1' AS 取得, array_agg(id ORDER BY created_at DESC, id DESC) AS ids
FROM (SELECT id, created_at FROM polls ORDER BY created_at DESC, id DESC LIMIT 5 OFFSET 0) p;

INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
VALUES (gen_random_uuid(), '割り込み', 'OPEN', 'ZZZZZZZZ', false, now() + interval '1 hour', now() + interval '8 days', now());

SELECT 'ページ2（OFFSET）' AS 取得, array_agg(id ORDER BY created_at DESC, id DESC) AS ids
FROM (SELECT id, created_at FROM polls ORDER BY created_at DESC, id DESC LIMIT 5 OFFSET 5) p;

SELECT 'ページ2（キーセット）' AS 取得, array_agg(id ORDER BY created_at DESC, id DESC) AS ids
FROM (SELECT id, created_at FROM polls
      WHERE (created_at, id) < ((SELECT created_at FROM polls WHERE id = 7), 7)
      ORDER BY created_at DESC, id DESC LIMIT 5) p;
ROLLBACK;
