package com.example.quickvote.poll;

import java.time.Instant;
import java.util.List;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OptionRepository {

    /** 選択肢と、その得票数。 */
    public record OptionCount(long id, String label, int votes) {
    }

    private final JdbcClient jdbc;

    public OptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long pollId, String label, long createdBy, Instant now) {
        return jdbc.sql("""
                INSERT INTO options (poll_id, label, created_by, created_at)
                VALUES (:pollId, :label, :createdBy, :now)
                RETURNING id
                """)
                .param("pollId", pollId)
                .param("label", label)
                .param("createdBy", createdBy)
                .param("now", Db.ts(now))
                .query(Long.class)
                .single();
    }

    public int count(long pollId) {
        return jdbc.sql("SELECT count(*) FROM options WHERE poll_id = :pollId")
                .param("pollId", pollId)
                .query(Integer.class)
                .single();
    }

    public boolean existsInPoll(long pollId, long optionId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM options WHERE poll_id = :pollId AND id = :optionId)")
                .param("pollId", pollId)
                .param("optionId", optionId)
                .query(Boolean.class)
                .single();
    }

    /** LEFT JOIN なので、票が0の選択肢も 0 件として返る（INNER JOIN だと消える）。 */
    public List<OptionCount> findWithVoteCounts(long pollId) {
        return jdbc.sql("""
                SELECT o.id, o.label, count(v.participant_id) AS votes
                FROM options o
                LEFT JOIN votes v ON v.option_id = o.id
                WHERE o.poll_id = :pollId
                GROUP BY o.id, o.label
                ORDER BY o.id
                """)
                .param("pollId", pollId)
                .query((rs, n) -> new OptionCount(rs.getLong("id"), rs.getString("label"), rs.getInt("votes")))
                .list();
    }
}
