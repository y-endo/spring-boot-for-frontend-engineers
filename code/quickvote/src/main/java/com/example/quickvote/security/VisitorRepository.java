package com.example.quickvote.security;

import java.time.Instant;
import java.util.Optional;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VisitorRepository {

    private final JdbcClient jdbc;

    public VisitorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findIdByTokenHash(String tokenHash) {
        return jdbc.sql("SELECT id FROM visitors WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .query(Long.class)
                .optional();
    }

    public long create(String tokenHash, Instant now) {
        return jdbc.sql("""
                INSERT INTO visitors (token_hash, created_at, last_seen_at)
                VALUES (:tokenHash, :now, :now)
                RETURNING id
                """)
                .param("tokenHash", tokenHash)
                .param("now", Db.ts(now))
                .query(Long.class)
                .single();
    }

    /**
     * 最終アクセス時刻の更新。リクエストのたびに書くと書き込みが増えるので、threshold より古いときだけ更新する。
     * 更新したら true（呼び出し側は Cookie の有効期限も延ばす）。
     */
    public boolean touch(long id, Instant now, Instant threshold) {
        return jdbc.sql("UPDATE visitors SET last_seen_at = :now WHERE id = :id AND last_seen_at < :threshold")
                .param("now", Db.ts(now))
                .param("id", id)
                .param("threshold", Db.ts(threshold))
                .update() > 0;
    }

    /** 定期削除用。長く使われていない訪問者（参加者・冪等性記録は CASCADE で一緒に消える）。 */
    public int deleteInactiveBatch(Instant cutoff, int limit) {
        return jdbc.sql("""
                DELETE FROM visitors WHERE id IN (
                    SELECT id FROM visitors WHERE last_seen_at < :cutoff ORDER BY last_seen_at LIMIT :limit)
                """)
                .param("cutoff", Db.ts(cutoff))
                .param("limit", limit)
                .update();
    }
}
