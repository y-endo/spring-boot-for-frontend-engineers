-- 2セッションの実験用データ（QuickVote と同じ表）。scripts/scenario.sh が、台本の前に流す。
--   訪問者 1〜60
--   投票1: 参加者49人（訪問者1〜49。1が主催者）、選択肢2つ。「残り1枠」
--   投票2: 参加者1人（主催者）。デッドロックの実験用
--   メモ1: 楽観ロックの実験用
INSERT INTO visitors (token_hash, created_at, last_seen_at)
SELECT md5(g::text) || md5((g + 100000)::text), now(), now() FROM generate_series(1, 60) g;

INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
VALUES (gen_random_uuid(), 'ラスト1枠', 'OPEN', 'SEATSEAT', false, now() + interval '1 hour', now() + interval '7 days', now()),
       (gen_random_uuid(), '二つ目の投票', 'OPEN', 'DEADDEAD', false, now() + interval '1 hour', now() + interval '7 days', now());

INSERT INTO participants (poll_id, visitor_id, role, joined_at)
SELECT 1, id, CASE WHEN id = 1 THEN 'HOST' ELSE 'MEMBER' END, now() FROM visitors WHERE id <= 49;
INSERT INTO participants (poll_id, visitor_id, role, joined_at) VALUES (2, 50, 'HOST', now());

INSERT INTO options (poll_id, label, created_by, created_at)
VALUES (1, 'カレー', 1, now()), (1, 'そば', 1, now());

INSERT INTO lab_memos (id, body) VALUES (1, '最初');
