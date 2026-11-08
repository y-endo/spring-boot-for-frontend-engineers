-- 新しい順に 20 件ずつ、50 ページぶん（1,000 件）取り出す。created_at だけで並べると、同じ時刻の行の順序は決まらない。
-- 同じ時刻の投票が何件あるか
SELECT created_at, count(*) AS 同時刻の件数 FROM polls GROUP BY created_at ORDER BY created_at DESC LIMIT 3;

-- ページごとに別々のクエリを投げたのと同じ結果を作る（LATERAL で各ページを独立に実行）
WITH pages AS (
  SELECT g AS page, p.id FROM generate_series(0, 49) g
  CROSS JOIN LATERAL (SELECT id FROM polls ORDER BY created_at DESC LIMIT 20 OFFSET g * 20) p
)
SELECT count(*) AS 取り出した行数, count(DISTINCT id) AS 重複なしの行数 FROM pages;

WITH pages AS (
  SELECT g AS page, p.id FROM generate_series(0, 49) g
  CROSS JOIN LATERAL (SELECT id FROM polls ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET g * 20) p
)
SELECT count(*) AS 取り出した行数, count(DISTINCT id) AS 重複なしの行数 FROM pages;
