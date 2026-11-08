package com.example.quickvote.invite;

import java.time.Instant;
import java.util.Optional;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InviteRepository {

    private final JdbcClient jdbc;

    public InviteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long pollId, String tokenHash, Instant now, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO invites (poll_id, token_hash, created_at, expires_at)
                VALUES (:pollId, :tokenHash, :now, :expiresAt)
                """)
                .param("pollId", pollId)
                .param("tokenHash", tokenHash)
                .param("now", Db.ts(now))
                .param("expiresAt", Db.ts(expiresAt))
                .update();
    }

    /** トークンから投票を引く（消費はしない）。使用済み・期限切れかどうかは consume() の更新件数で判定する。 */
    public Optional<Long> findPollId(String tokenHash) {
        return jdbc.sql("SELECT poll_id FROM invites WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .query(Long.class)
                .optional();
    }

    /**
     * 一度限りの消費。「未使用で、期限内」を WHERE に書いた UPDATE の更新件数で成否を判定する。
     * 先に SELECT で「未使用か」を調べてから UPDATE する書き方だと、2人が同時に SELECT を通り抜けてしまう。
     * UPDATE は1文の中で「条件の確認と書き込み」が行ロックの下で原子的に行われ、後続は更新後の値を見て0件になる。
     */
    public int consume(String tokenHash, long visitorId, Instant now) {
        return jdbc.sql("""
                UPDATE invites SET used_at = :now, used_by = :visitorId
                WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > :now
                """)
                .param("now", Db.ts(now))
                .param("visitorId", visitorId)
                .param("tokenHash", tokenHash)
                .update();
    }

    /** 定期削除用。期限を過ぎた招待（使用済みを含む）を消す。 */
    public int deleteExpiredBatch(Instant now, int limit) {
        return jdbc.sql("""
                DELETE FROM invites WHERE id IN (
                    SELECT id FROM invites WHERE expires_at <= :now ORDER BY expires_at LIMIT :limit)
                """)
                .param("now", Db.ts(now))
                .param("limit", limit)
                .update();
    }
}
