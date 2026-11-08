-- 第4部の実験専用の表（QuickVote 本体にはない）。

-- 第23章: REQUIRES_NEW の実験用。「監査ログ」は、業務がロールバックされても残したい。
CREATE TABLE lab_audit (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message text NOT NULL
);

-- 第26章: 楽観ロックの実験用。version 列で「読んだ後に他人が更新したか」を検出する。
CREATE TABLE lab_memos (
    id      bigint PRIMARY KEY,
    body    text   NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
