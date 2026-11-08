-- 【contract（絞る）その1】リリース N+1 の後。
-- 前提: 動いているアプリがすべて「title と name の両方を書く」バージョンになっていること。
-- 古いバージョンが1台でも残っていると、name を書かない INSERT が NOT NULL 違反で失敗する。
ALTER TABLE polls ALTER COLUMN name SET NOT NULL;
ALTER TABLE polls ADD CONSTRAINT polls_name_check CHECK (char_length(name) >= 1);
