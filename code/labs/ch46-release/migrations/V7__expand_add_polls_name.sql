-- 【expand（広げる）】リリース N（リファレンス実装の V6 の次の版として）。polls.title を polls.name に改名したい、という変更の第1段。
-- 古い列（title）は消さない。新しい列（name）を「足す」だけなので、古いバージョンのアプリは何も知らずに動き続ける。
-- この時点では name は NULL を許す。NOT NULL にするのは、全インスタンスが name を書くようになってから。
ALTER TABLE polls ADD COLUMN name text;

-- 既存の行を埋める（バックフィル）。大きなテーブルでは、1回で全件を更新せず、件数を区切って繰り返す。
UPDATE polls SET name = title WHERE name IS NULL;
