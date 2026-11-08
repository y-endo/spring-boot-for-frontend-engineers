-- 仕様変更: タイトルを 200 文字まで許す。適用済みの V1 は触らず、新しい V を足す。
ALTER TABLE polls ALTER COLUMN title TYPE varchar(200);
