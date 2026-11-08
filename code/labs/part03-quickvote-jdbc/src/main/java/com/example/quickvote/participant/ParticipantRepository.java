package com.example.quickvote.participant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepository {

    public record Participant(long id, long pollId, long visitorId, ParticipantRole role) {
    }

    private final JdbcClient jdbc;

    public ParticipantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 公開ID(UUID)と訪問者から、参加者としての文脈を探す。
     * 「投票が存在しない」と「存在するが参加していない」は、どちらも空になる。
     * 呼び出し側はどちらも同じ404にするので、存在の有無が外に漏れない。
     */
    public Optional<ParticipantContext> findActive(UUID pollPublicId, long visitorId) {
        return jdbc.sql("""
                SELECT p.id AS poll_id, pt.id AS participant_id, pt.role
                FROM polls p
                JOIN participants pt ON pt.poll_id = p.id
                WHERE p.public_id = :publicId AND pt.visitor_id = :visitorId AND pt.revoked_at IS NULL
                """)
                .param("publicId", pollPublicId)
                .param("visitorId", visitorId)
                .query((rs, n) -> new ParticipantContext(rs.getLong("poll_id"), pollPublicId,
                        rs.getLong("participant_id"), ParticipantRole.valueOf(rs.getString("role"))))
                .optional();
    }

    public Optional<Participant> findByPollAndVisitor(long pollId, long visitorId) {
        return jdbc.sql("""
                SELECT id, poll_id, visitor_id, role FROM participants
                WHERE poll_id = :pollId AND visitor_id = :visitorId AND revoked_at IS NULL
                """)
                .param("pollId", pollId)
                .param("visitorId", visitorId)
                .query((rs, n) -> new Participant(rs.getLong("id"), rs.getLong("poll_id"), rs.getLong("visitor_id"),
                        ParticipantRole.valueOf(rs.getString("role"))))
                .optional();
    }

    public int countActive(long pollId) {
        return jdbc.sql("SELECT count(*) FROM participants WHERE poll_id = :pollId AND revoked_at IS NULL")
                .param("pollId", pollId)
                .query(Integer.class)
                .single();
    }

    public long insert(long pollId, long visitorId, ParticipantRole role, Instant now) {
        return jdbc.sql("""
                INSERT INTO participants (poll_id, visitor_id, role, joined_at)
                VALUES (:pollId, :visitorId, :role, :now)
                RETURNING id
                """)
                .param("pollId", pollId)
                .param("visitorId", visitorId)
                .param("role", role.name())
                .param("now", Db.ts(now))
                .query(Long.class)
                .single();
    }
}
