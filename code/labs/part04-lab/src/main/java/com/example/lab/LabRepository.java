package com.example.lab;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 実験用の最小のリポジトリ。QuickVote 本体（第3部の JdbcClient 版）と同じテーブルに、同じ形の SQL を投げる。
 * ここにはトランザクションの指定が一切ない。境界は、呼び出す側（Service）が決める。
 */
@Repository
public class LabRepository {

    private final JdbcClient jdbc;

    public LabRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insertVisitor() {
        // token_hash は char(64) UNIQUE。実験なので UUID を2つ連結して作る。
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        return jdbc.sql("""
                INSERT INTO visitors (token_hash, created_at, last_seen_at)
                VALUES (:hash, now(), now()) RETURNING id
                """)
                .param("hash", hash)
                .query(Long.class)
                .single();
    }

    public long insertPoll(String title, String joinCode) {
        return jdbc.sql("""
                INSERT INTO polls (public_id, title, status, join_code, allow_member_options,
                                   closes_at, delete_after, created_at)
                VALUES (:publicId, :title, 'OPEN', :joinCode, false,
                        now() + interval '1 hour', now() + interval '7 days', now())
                RETURNING id
                """)
                .param("publicId", UUID.randomUUID())
                .param("title", title)
                .param("joinCode", joinCode)
                .query(Long.class)
                .single();
    }

    public long insertParticipant(long pollId, long visitorId, String role) {
        return jdbc.sql("""
                INSERT INTO participants (poll_id, visitor_id, role, joined_at)
                VALUES (:pollId, :visitorId, :role, now()) RETURNING id
                """)
                .param("pollId", pollId)
                .param("visitorId", visitorId)
                .param("role", role)
                .query(Long.class)
                .single();
    }

    public int countParticipants(long pollId) {
        return jdbc.sql("SELECT count(*) FROM participants WHERE poll_id = :pollId AND revoked_at IS NULL")
                .param("pollId", pollId)
                .query(Integer.class)
                .single();
    }

    /** 親行（polls）の排他ロック。QuickVote 本体の PollRepository.lockById と同じ SQL。 */
    public Optional<Long> lockPoll(long pollId) {
        return jdbc.sql("SELECT id FROM polls WHERE id = :id FOR UPDATE")
                .param("id", pollId)
                .query(Long.class)
                .optional();
    }

    public long countRows(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    /** 「いまこの処理が使っているDB接続」の識別子。同じ値なら同じ接続（同じトランザクションの可能性がある）。 */
    public int backendPid() {
        return jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();
    }

    public void insertAudit(String message) {
        jdbc.sql("INSERT INTO lab_audit (message) VALUES (:m)").param("m", message).update();
    }
}
