-- ロックを取ったまま、何もしないトランザクション（アプリのバグや、外部通信の待ちを想定）
SET idle_in_transaction_session_timeout = '3s';
BEGIN;
SELECT id FROM polls LIMIT 1 FOR UPDATE;
\echo ロックを取った。ここから 6 秒、何もしない
\! sleep 6
SELECT 1;
