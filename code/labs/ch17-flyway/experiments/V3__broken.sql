-- 2文目で失敗する。PostgreSQL は DDL もトランザクション内なので、1文目の ADD COLUMN も取り消される。
ALTER TABLE polls ADD COLUMN memo text;
ALTER TABLE nosuch_table ADD COLUMN x int;
