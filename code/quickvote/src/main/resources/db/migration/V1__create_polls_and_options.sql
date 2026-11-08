-- 第17章: 最初のマイグレーション。投票（polls）と選択肢（options）。
-- 主キーは内部用の bigint identity。API で外に出すのは推測できない public_id（UUID）だけにする。

CREATE TABLE polls (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            uuid         NOT NULL UNIQUE,
    title                varchar(100) NOT NULL,
    status               varchar(10)  NOT NULL,
    join_code            char(8)      NOT NULL UNIQUE,
    allow_member_options boolean      NOT NULL,
    closes_at            timestamptz  NOT NULL,
    delete_after         timestamptz  NOT NULL,
    created_at           timestamptz  NOT NULL,
    -- 単一行で守れる条件は CHECK に書く（アプリの if をすり抜けても DB が止める最終防衛線）。
    CONSTRAINT polls_status_check CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT polls_title_check CHECK (char_length(title) >= 1),
    -- 参加コードは「紛らわしい文字を除いた31種」の8文字。
    CONSTRAINT polls_join_code_check CHECK (join_code ~ '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$'),
    CONSTRAINT polls_closes_at_check CHECK (closes_at >= created_at),
    CONSTRAINT polls_delete_after_check CHECK (delete_after >= closes_at)
);

-- 期限切れ削除（スケジューラ）が delete_after で範囲検索するため。
CREATE INDEX polls_delete_after_idx ON polls (delete_after);

CREATE TABLE options (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id    bigint       NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label      varchar(100) NOT NULL,
    -- 作成した参加者（participants.id）。participants は V2 で作るので、外部キーは V2 で足す。
    created_by bigint       NOT NULL,
    created_at timestamptz  NOT NULL,
    CONSTRAINT options_label_check CHECK (char_length(label) >= 1 AND octet_length(label) <= 400),
    -- votes から (poll_id, option_id) の複合外部キーで参照するため。
    -- 「投票先の選択肢は同じ投票のものでなければならない」をDBに守らせる。
    CONSTRAINT options_poll_id_id_key UNIQUE (poll_id, id)
);

CREATE INDEX options_poll_id_idx ON options (poll_id);
