-- 第37章: SSE のイベント履歴。業務更新と同じトランザクションで保存する。
-- seq は投票（polls 行）ごとの連番。polls.last_event_seq を、行ロックを持ったまま +1 して採番する。
-- シーケンスオブジェクトは使わない（採番順とコミット順が一致する保証がないため）。

ALTER TABLE polls ADD COLUMN last_event_seq bigint NOT NULL DEFAULT 0;

CREATE TABLE poll_events (
    poll_id    bigint      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    seq        bigint      NOT NULL,
    type       varchar(40) NOT NULL,
    payload    jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (poll_id, seq)
);
