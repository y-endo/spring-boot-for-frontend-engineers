-- 第33章: 共同主催者の招待。トークン本体は保存せず、SHA-256（16進）だけを持つ。
-- 一度限りの消費は used_at IS NULL を条件にした UPDATE の更新件数で判定する。

CREATE TABLE invites (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    bigint      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    token_hash char(64)    NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    used_by    bigint REFERENCES visitors (id) ON DELETE SET NULL,
    CONSTRAINT invites_used_check CHECK (used_by IS NULL OR used_at IS NOT NULL)
);

CREATE INDEX invites_poll_idx ON invites (poll_id);
CREATE INDEX invites_expires_at_idx ON invites (expires_at);
