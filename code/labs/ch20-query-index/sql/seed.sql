-- 第20章: 検索・インデックスの実験用データ。投票3万件・選択肢12万件・参加者約60万件・票約40万件。
-- 参加コードは「紛らわしい文字を除いた31種」から、番号を31進数に直して作る（重複しない）。
\timing off
\set ON_ERROR_STOP on

INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
SELECT gen_random_uuid(),
       '投票 ' || g,
       CASE WHEN g % 10 = 0 THEN 'CLOSED' ELSE 'OPEN' END,
       (SELECT string_agg(substr('ABCDEFGHJKMNPQRSTUVWXYZ23456789',
                                 1 + (((g::bigint * 7919) / (31::bigint ^ k)::bigint) % 31)::int, 1), '' ORDER BY k)
        FROM generate_series(0, 7) k),
       g % 3 = 0,
       -- 作成日時は「時間」単位に丸める。同じ時刻の投票がたくさんできる（並べ替えの実験のため）
       date_trunc('hour', now() - (g * interval '86 seconds')) + interval '1 hour',
       date_trunc('hour', now() - (g * interval '86 seconds')) + interval '8 days',
       date_trunc('hour', now() - (g * interval '86 seconds'))
FROM generate_series(1, 30000) g;

-- 選択肢: 各投票に4つ。作成者は後で決める（外部キーのため、先に参加者が要る）。
-- options.created_by は NOT NULL かつ participants への外部キーなので、投票ごとの主催者を先に作る。
INSERT INTO participants (poll_id, visitor_id, role, joined_at)
SELECT id, 1000000 + id, 'HOST', created_at FROM polls;

INSERT INTO options (poll_id, label, created_by, created_at)
SELECT p.id, '案' || k, pt.id, p.created_at
FROM polls p
JOIN participants pt ON pt.poll_id = p.id AND pt.role = 'HOST'
CROSS JOIN generate_series(1, 4) k
ORDER BY p.id, k;

-- 参加者: 主催者のほかに、投票ごとに 0〜38 人（平均 19 人）の一般参加者。訪問者IDは 10万人の中から。
INSERT INTO participants (poll_id, visitor_id, role, joined_at, revoked_at)
SELECT p.id,
       1 + ((p.id * 7 + g) % 100000),
       'MEMBER',
       p.created_at + interval '1 minute',
       CASE WHEN (p.id + g) % 25 = 0 THEN p.created_at + interval '5 minutes' END  -- 4%は退出済み（NULLと非NULLが混ざる）
FROM polls p
CROSS JOIN LATERAL generate_series(1, (p.id * 13 % 39)::int) g;

-- 票: 一般参加者の約 7 割が、自分の投票の選択肢のどれかに投票する。
INSERT INTO votes (poll_id, participant_id, option_id, voted_at)
SELECT pt.poll_id, pt.id,
       (SELECT o.id FROM options o WHERE o.poll_id = pt.poll_id ORDER BY o.id OFFSET (pt.id % 4) LIMIT 1),
       pt.joined_at + interval '2 minutes'
FROM participants pt
WHERE pt.role = 'MEMBER' AND pt.revoked_at IS NULL AND pt.id % 10 < 7;
