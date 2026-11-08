-- 投票 43 は、票が 0 件の選択肢を 1 つ持つ。まず LEFT JOIN + count(列) で数える（正しい形）
SELECT o.id, o.label, count(v.participant_id) AS 票数
FROM options o LEFT JOIN votes v ON v.option_id = o.id
WHERE o.poll_id = 43 GROUP BY o.id, o.label ORDER BY o.id;

-- INNER JOIN だと、票 0 件の選択肢が消える
SELECT o.id, o.label, count(v.participant_id) AS 票数
FROM options o JOIN votes v ON v.option_id = o.id
WHERE o.poll_id = 43 GROUP BY o.id, o.label ORDER BY o.id;

-- LEFT JOIN でも count(*) だと、票 0 件が「1」になる（結合した行そのものを数えるため）
SELECT o.id, o.label, count(*) AS 間違った票数
FROM options o LEFT JOIN votes v ON v.option_id = o.id
WHERE o.poll_id = 43 GROUP BY o.id, o.label ORDER BY o.id;

-- NULL: 投票 2 の参加者 27 人のうち、1 人は退出済み（revoked_at が NULL でない）
SELECT count(*) AS 全員, count(revoked_at) AS count列は_NULLを数えない,
       count(*) FILTER (WHERE revoked_at IS NULL) AS 在籍中
FROM participants WHERE poll_id = 2;

SELECT count(*) AS イコールNULL FROM participants WHERE poll_id = 2 AND revoked_at = NULL;
SELECT count(*) AS IS_NULL     FROM participants WHERE poll_id = 2 AND revoked_at IS NULL;
-- <> も同じ。「退出済みでない」を revoked_at <> 時刻 と書くと、NULL の人（在籍中の全員）が消える
SELECT count(*) AS 不等号 FROM participants WHERE poll_id = 2 AND revoked_at <> now();

-- NOT IN に NULL が混ざると、全部が消える
SELECT count(*) AS NULLなし FROM participants WHERE poll_id = 2 AND id NOT IN (1, 2);
SELECT count(*) AS NULLあり FROM participants WHERE poll_id = 2 AND id NOT IN (1, 2, NULL);
