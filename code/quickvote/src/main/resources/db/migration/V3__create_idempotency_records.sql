-- 第27章: 冪等性。キーの範囲は「訪問者 × Idempotency-Key」。
-- 業務更新と同じトランザクションで書くので、「業務更新は成功したが記録がない」状態は作れない。

CREATE TABLE idempotency_records (
    visitor_id    bigint       NOT NULL REFERENCES visitors (id) ON DELETE CASCADE,
    idem_key      varchar(200) NOT NULL,
    -- 同じキーで別の入力が来たことを検出するための、リクエスト内容のSHA-256（16進）。
    request_hash  char(64)     NOT NULL,
    -- 保存した応答のHTTPステータス。トランザクション内で先に行を作るだけの間は 0（未完了）。
    status        smallint     NOT NULL,
    response_body text,
    created_at    timestamptz  NOT NULL,
    PRIMARY KEY (visitor_id, idem_key)
);

-- 保持期限（24時間）を過ぎた行の削除用。
CREATE INDEX idempotency_records_created_at_idx ON idempotency_records (created_at);
