package com.example.quickvote.common;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    public record Record(String requestHash, int status, String responseBody) {
    }

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 期限切れの記録を消す。定期削除ジョブが動く前でも、期限を過ぎたキーは再利用できるようにするため。
     */
    public int deleteExpired(long visitorId, String key, Instant cutoff) {
        return jdbc.sql("""
                DELETE FROM idempotency_records
                WHERE visitor_id = :visitorId AND idem_key = :key AND created_at < :cutoff
                """)
                .param("visitorId", visitorId)
                .param("key", key)
                .param("cutoff", Db.ts(cutoff))
                .update();
    }

    /**
     * キーの「先取り」。1を返せば自分が最初の実行者、0なら他のトランザクションが先に確定させている。
     * <p>
     * ON CONFLICT DO NOTHING は、競合相手がまだコミットしていないとき、その結果が出るまで待つ。
     * 相手がコミットすれば0件（＝自分は後続）、ロールバックすれば自分が挿入できる。
     * 「同時に同じキー」を、アプリ側のロックなしでDBに直列化させている。
     */
    public int tryInsert(long visitorId, String key, String requestHash, Instant now) {
        return jdbc.sql("""
                INSERT INTO idempotency_records (visitor_id, idem_key, request_hash, status, created_at)
                VALUES (:visitorId, :key, :requestHash, 0, :now)
                ON CONFLICT (visitor_id, idem_key) DO NOTHING
                """)
                .param("visitorId", visitorId)
                .param("key", key)
                .param("requestHash", requestHash)
                .param("now", Db.ts(now))
                .update();
    }

    public void complete(long visitorId, String key, int status, String responseBody) {
        jdbc.sql("""
                UPDATE idempotency_records SET status = :status, response_body = :body
                WHERE visitor_id = :visitorId AND idem_key = :key
                """)
                .param("status", status)
                .param("body", responseBody)
                .param("visitorId", visitorId)
                .param("key", key)
                .update();
    }

    /**
     * 確定済みの記録を、共有ロック（FOR SHARE）をかけて読む。なければ空。
     * ロックをかけると、読んでいる間にその行が定期削除に消されることがない（削除は、このトランザクションの終わりまで待たされる）。
     * 空が返るのは、tryInsert が0件だった後、このSELECTの前に、記録が期限切れで削除されてコミットされた場合。
     * 呼び出し側は先取りからやり直す。
     */
    public Optional<Record> findForShare(long visitorId, String key) {
        return jdbc.sql("""
                SELECT request_hash, status, response_body FROM idempotency_records
                WHERE visitor_id = :visitorId AND idem_key = :key
                FOR SHARE
                """)
                .param("visitorId", visitorId)
                .param("key", key)
                .query((rs, n) -> new Record(rs.getString("request_hash"), rs.getInt("status"),
                        rs.getString("response_body")))
                .optional();
    }

    /** 定期削除用。1回の削除件数を limit で区切る。 */
    public int deleteExpiredBatch(Instant cutoff, int limit) {
        return jdbc.sql("""
                DELETE FROM idempotency_records
                WHERE (visitor_id, idem_key) IN (
                    SELECT visitor_id, idem_key FROM idempotency_records
                    WHERE created_at < :cutoff
                    ORDER BY created_at
                    LIMIT :limit)
                """)
                .param("cutoff", Db.ts(cutoff))
                .param("limit", limit)
                .update();
    }
}
