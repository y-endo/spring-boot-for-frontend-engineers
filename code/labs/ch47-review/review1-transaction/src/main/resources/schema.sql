-- 演習用の最小スキーマ。QuickVote の polls / participants / options / poll_events を簡略化したもの。
CREATE TABLE IF NOT EXISTS polls (
    id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title  text   NOT NULL,
    status text   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS participants (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    bigint NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    visitor_id bigint NOT NULL,
    role       text   NOT NULL DEFAULT 'MEMBER',
    UNIQUE (poll_id, visitor_id)
);

CREATE TABLE IF NOT EXISTS options (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id bigint NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label   text   NOT NULL CHECK (char_length(label) BETWEEN 1 AND 100)
);

CREATE TABLE IF NOT EXISTS poll_events (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id bigint NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    type    text   NOT NULL
);
