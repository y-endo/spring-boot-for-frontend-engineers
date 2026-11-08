-- options.created_by の外部キーを、単独列 (created_by) から複合 (poll_id, created_by) に置き換える。
--
-- 何が問題だったか（V2 のバグ）:
--   V2 は options.created_by を participants (id) だけで参照していた。participants.id は全投票で通しの番号なので、
--   「別の投票の参加者」を作成者にした選択肢が INSERT できてしまう。すると、投票1を削除したとき
--   participants（投票1のもの）が CASCADE で消え、それを created_by に持つ「投票2の選択肢」まで連鎖して消える。
--   votes は V2 の時点から (poll_id, participant_id) / (poll_id, option_id) の複合外部キーで守られていたが、
--   options.created_by だけが単独列だった。
--
-- 直し方:
--   参照先は participants の UNIQUE (poll_id, id)（V2 の participants_poll_id_id_key。votes の複合外部キーが使っているもの）。
--   新しく UNIQUE を足す必要はない。
--   ON DELETE CASCADE は従来の方針（参加者が消えたらその人の選択肢も消える）のまま変えない。
--
-- 既存データがあっても流れるか（デプロイ前に確認すること）:
--   ADD CONSTRAINT は、既存の全行を新しい制約で検査する。options.poll_id と、created_by が指す participants.poll_id が
--   食い違う行（= このバグで入ってしまった行）が1行でもあれば、この版は失敗する。Flyway はこの版をトランザクションで
--   流すので、失敗したら DROP も取り消され、スキーマは V5 の状態のまま残る（途中まで適用された状態にはならない）。
--   違反行が見つかったら、次の SQL で洗い出し、どう直すか（作成者を同じ投票の HOST に付け替える／行を消す）を
--   人間が判断してから、この版を再実行する。自動で消さないのは、データを黙って失わないため。
--
--     SELECT o.id, o.poll_id, o.created_by, p.poll_id AS creator_poll_id
--       FROM options o JOIN participants p ON p.id = o.created_by
--      WHERE p.poll_id <> o.poll_id;
--
--   行数が非常に多い本番表では、ADD CONSTRAINT ... NOT VALID で先に制約を足し（新しい行から検査される）、
--   あとで VALIDATE CONSTRAINT を別の版で流す（弱いロックで検査できる）方法もある。この教材の規模では1回で足す。

ALTER TABLE options
    DROP CONSTRAINT options_created_by_fkey;

ALTER TABLE options
    ADD CONSTRAINT options_created_by_fkey FOREIGN KEY (poll_id, created_by)
        REFERENCES participants (poll_id, id) ON DELETE CASCADE;
