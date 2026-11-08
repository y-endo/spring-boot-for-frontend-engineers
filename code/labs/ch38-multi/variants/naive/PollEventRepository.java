package com.example.quickvote.event;

import java.time.Instant;
import java.util.List;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PollEventRepository {

    /** NOTIFY のチャンネル名。PollEventListener が LISTEN する。 */
    public static final String CHANNEL = "poll_events";

    private final JdbcClient jdbc;

    public PollEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 連番の採番。polls 行の last_event_seq を +1 して、新しい値を受け取る。
     * UPDATE は行ロックを取り、それはトランザクションの終わりまで続く。
     * だから「採番した順 = コミットされる順」になり、seq が小さいイベントが後から見えることがない。
     * （シーケンスオブジェクトは採番順とコミット順が一致しないので使わない）
     */
    public long nextSeq(long pollId) {
        return jdbc.sql("UPDATE polls SET last_event_seq = last_event_seq + 1 WHERE id = :pollId RETURNING last_event_seq")
                .param("pollId", pollId)
                .query(Long.class)
                .single();
    }

    public void insert(long pollId, long seq, String type, String payloadJson, Instant now) {
        jdbc.sql("""
                INSERT INTO poll_events (poll_id, seq, type, payload, created_at)
                VALUES (:pollId, :seq, :type, CAST(:payload AS jsonb), :now)
                """)
                .param("pollId", pollId)
                .param("seq", seq)
                .param("type", type)
                .param("payload", payloadJson)
                .param("now", Db.ts(now))
                .update();
    }

    /** [lab] pg_notify を呼ばない版。インスタンス間の通知はしない（第38章の「メモリ配信のみ」の再現用）。 */
    public void notifyPoll(long pollId) {
        // 何もしない
    }

    /** afterSeq より後のイベントを古い順に返す。 */
    public List<PollEvent> findAfter(long pollId, long afterSeq, int limit) {
        return jdbc.sql("""
                SELECT seq, type, payload::text AS payload, created_at FROM poll_events
                WHERE poll_id = :pollId AND seq > :afterSeq
                ORDER BY seq
                LIMIT :limit
                """)
                .param("pollId", pollId)
                .param("afterSeq", afterSeq)
                .param("limit", limit)
                .query((rs, n) -> new PollEvent(rs.getLong("seq"), rs.getString("type"), rs.getString("payload"),
                        Db.instant(rs, "created_at")))
                .list();
    }

    /** 現在の最新seq。投票が存在しなければ 0。 */
    public long lastSeq(long pollId) {
        return jdbc.sql("SELECT last_event_seq FROM polls WHERE id = :pollId")
                .param("pollId", pollId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }
}
