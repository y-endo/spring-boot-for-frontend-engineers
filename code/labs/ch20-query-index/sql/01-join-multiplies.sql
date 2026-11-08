-- 投票 100 の「選択肢の数」と「参加者の数」を、JOIN 1回でまとめて数えようとする
SELECT p.id,
       (SELECT count(*) FROM options      WHERE poll_id = p.id) AS 正しい選択肢数,
       (SELECT count(*) FROM participants WHERE poll_id = p.id) AS 正しい参加者数
FROM polls p WHERE p.id = 100;

SELECT p.id, count(o.id) AS 選択肢数, count(pt.id) AS 参加者数
FROM polls p
JOIN options o       ON o.poll_id  = p.id
JOIN participants pt ON pt.poll_id = p.id
WHERE p.id = 100
GROUP BY p.id;

-- DISTINCT で数え直すと合うが、行数は増えたまま（遅い・メモリを食う）
SELECT p.id, count(DISTINCT o.id) AS 選択肢数, count(DISTINCT pt.id) AS 参加者数
FROM polls p
JOIN options o       ON o.poll_id  = p.id
JOIN participants pt ON pt.poll_id = p.id
WHERE p.id = 100
GROUP BY p.id;

-- 集計してから JOIN する（1行ずつの関係に戻してから結ぶ）
SELECT p.id, o.n AS 選択肢数, pt.n AS 参加者数
FROM polls p
JOIN (SELECT poll_id, count(*) AS n FROM options      GROUP BY poll_id) o  ON o.poll_id  = p.id
JOIN (SELECT poll_id, count(*) AS n FROM participants GROUP BY poll_id) pt ON pt.poll_id = p.id
WHERE p.id = 100;
