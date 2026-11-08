-- 第18章（データモデルと制約設計）＋第32章（匿名セッション）で扱う表。
-- participants が visitors を参照するため、visitors もここで作る（外部キーの依存順）。

-- 訪問者: Cookie の訪問者トークンに対応する匿名の利用主体。
-- トークン本体は保存せず、SHA-256 を16進文字列にしたものだけを持つ。
CREATE TABLE visitors (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash   char(64)    NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL
);

-- 参加者: 「どの訪問者が、どの投票に、どの役割で参加しているか」。権限判定の根拠はこの表。
CREATE TABLE participants (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    bigint      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    visitor_id bigint      NOT NULL REFERENCES visitors (id) ON DELETE CASCADE,
    role       varchar(10) NOT NULL,
    joined_at  timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT participants_role_check CHECK (role IN ('HOST', 'MEMBER')),
    -- 同じ訪問者が同じ投票に二重に参加できない。
    CONSTRAINT participants_poll_visitor_key UNIQUE (poll_id, visitor_id),
    -- votes から複合外部キーで参照するため。
    CONSTRAINT participants_poll_id_id_key UNIQUE (poll_id, id)
);

CREATE INDEX participants_visitor_idx ON participants (visitor_id);

-- V1 の options.created_by に外部キーを足す。
ALTER TABLE options
    ADD CONSTRAINT options_created_by_fkey FOREIGN KEY (created_by) REFERENCES participants (id) ON DELETE CASCADE;

-- 票: 1人1票は UNIQUE(poll_id, participant_id) で守る（最終防衛線）。
CREATE TABLE votes (
    poll_id        bigint      NOT NULL,
    participant_id bigint      NOT NULL,
    option_id      bigint      NOT NULL,
    voted_at       timestamptz NOT NULL,
    CONSTRAINT votes_poll_participant_key UNIQUE (poll_id, participant_id),
    -- 複合外部キー: 参加者も選択肢も「同じ poll_id のもの」しか指せない。
    CONSTRAINT votes_participant_fkey FOREIGN KEY (poll_id, participant_id)
        REFERENCES participants (poll_id, id) ON DELETE CASCADE,
    CONSTRAINT votes_option_fkey FOREIGN KEY (poll_id, option_id)
        REFERENCES options (poll_id, id) ON DELETE CASCADE
);

CREATE INDEX votes_option_idx ON votes (option_id);
