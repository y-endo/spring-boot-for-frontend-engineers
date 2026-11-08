-- 演習用の最小スキーマ。QuickVote の polls / options を簡略化したもの。
CREATE TABLE IF NOT EXISTS polls (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           text        NOT NULL,
    join_code       text        NOT NULL,
    host_visitor_id bigint      NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    delete_after    timestamptz NOT NULL DEFAULT now() + interval '7 days',
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS options (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id bigint NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label   text   NOT NULL CHECK (char_length(label) BETWEEN 1 AND 100)
);
