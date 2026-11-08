package com.example.quickvote.vote;

import java.time.Instant;
import java.util.Optional;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VoteRepository {

    private final JdbcClient jdbc;

    public VoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 投票（上書き）。UNIQUE(poll_id, participant_id) があるので、同じ人の票は何があっても1行。
     * <ul>
     * <li>初めての投票 → INSERT（1件）</li>
     * <li>別の選択肢への変更 → DO UPDATE（1件）</li>
     * <li>同じ選択肢への再投票 → WHERE が偽になり何もしない（0件）。PUT が冪等であることの現れ</li>
     * </ul>
     * 戻り値の更新件数で「票が実際に変わったか」が分かるので、イベントを出すかどうかの判定に使う。
     */
    public int upsert(long pollId, long participantId, long optionId, Instant now) {
        return jdbc.sql("""
                INSERT INTO votes (poll_id, participant_id, option_id, voted_at)
                VALUES (:pollId, :participantId, :optionId, :now)
                ON CONFLICT (poll_id, participant_id) DO UPDATE
                   SET option_id = EXCLUDED.option_id, voted_at = EXCLUDED.voted_at
                   WHERE votes.option_id <> EXCLUDED.option_id
                """)
                .param("pollId", pollId)
                .param("participantId", participantId)
                .param("optionId", optionId)
                .param("now", Db.ts(now))
                .update();
    }

    public Optional<Long> findOptionId(long pollId, long participantId) {
        return jdbc.sql("SELECT option_id FROM votes WHERE poll_id = :pollId AND participant_id = :participantId")
                .param("pollId", pollId)
                .param("participantId", participantId)
                .query(Long.class)
                .optional();
    }
}
