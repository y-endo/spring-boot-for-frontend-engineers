-- 第8部の実験用スキーマ。QuickVote の polls / options / votes を、JPA の比較に必要な列だけに簡略化したもの。
-- （本番の QuickVote は code/quickvote/src/main/resources/db/migration/ にある。participants 表は省略し、
--   participant_id は単なる数値の列にしている。）

CREATE TABLE polls (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title     varchar(100) NOT NULL,
    status    varchar(10)  NOT NULL,
    closes_at timestamptz  NOT NULL,
    -- 楽観ロック用（第40章）。JPA の @Version が参照する。UPDATE のたびに 1 増える。
    version   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT polls_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE options (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id bigint       NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label   varchar(100) NOT NULL,
    -- votes から複合外部キーで参照するため（QuickVote と同じ）
    CONSTRAINT options_poll_id_id_key UNIQUE (poll_id, id)
);

CREATE INDEX options_poll_id_idx ON options (poll_id);

CREATE TABLE votes (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id        bigint      NOT NULL,
    option_id      bigint      NOT NULL,
    participant_id bigint      NOT NULL,
    voted_at       timestamptz NOT NULL,
    -- QuickVote と同じ「1人1票」。JPA には複合キー制約を表す場所がないので、DBに任せる。
    CONSTRAINT votes_poll_participant_key UNIQUE (poll_id, participant_id),
    -- 「選択肢は同じ投票のもの」をDBが守る複合外部キー（QuickVote と同じ）
    CONSTRAINT votes_option_fkey FOREIGN KEY (poll_id, option_id)
        REFERENCES options (poll_id, id) ON DELETE CASCADE
);

CREATE INDEX votes_option_idx ON votes (option_id);
