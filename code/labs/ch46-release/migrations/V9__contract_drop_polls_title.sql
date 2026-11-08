-- 【contract（絞る）その2】リリース N+2 の後。
-- 前提: 動いているアプリがどれも title を読み書きしないこと（コードから title が消えて、デプロイが一巡した後）。
-- ここまで来て初めて古い列を消す。DROP COLUMN は元に戻せない（戻すならバックアップから）。
ALTER TABLE polls DROP COLUMN title;
