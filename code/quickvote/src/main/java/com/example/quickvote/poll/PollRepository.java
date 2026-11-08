package com.example.quickvote.poll;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PollRepository {

    private final JdbcClient jdbc;

    public PollRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Poll map(ResultSet rs, int rowNum) throws SQLException {
        return new Poll(
                rs.getLong("id"),
                rs.getObject("public_id", UUID.class),
                rs.getString("title"),
                PollStatus.valueOf(rs.getString("status")),
                rs.getString("join_code"),
                rs.getBoolean("allow_member_options"),
                Db.instant(rs, "closes_at"),
                Db.instant(rs, "delete_after"),
                Db.instant(rs, "created_at"),
                rs.getLong("last_event_seq"));
    }

    /**
     * 参加コードが既存と重複すると UNIQUE 制約違反（DuplicateKeyException）になる。
     * 呼び出し側（PollService）が別のコードで取り直す。
     */
    public long insert(UUID publicId, String title, String joinCode, boolean allowMemberOptions, Instant closesAt,
            Instant now) {
        return jdbc.sql("""
                INSERT INTO polls (public_id, title, status, join_code, allow_member_options,
                                   closes_at, delete_after, created_at)
                VALUES (:publicId, :title, 'OPEN', :joinCode, :allowMemberOptions,
                        :closesAt, :deleteAfter, :now)
                RETURNING id
                """)
                .param("publicId", publicId)
                .param("title", title)
                .param("joinCode", joinCode)
                .param("allowMemberOptions", allowMemberOptions)
                .param("closesAt", Db.ts(closesAt))
                .param("deleteAfter", Db.ts(now.plus(PollRules.RETENTION)))
                .param("now", Db.ts(now))
                .query(Long.class)
                .single();
    }

    public Optional<Poll> findById(long id) {
        return jdbc.sql("SELECT * FROM polls WHERE id = :id")
                .param("id", id)
                .query(PollRepository::map)
                .optional();
    }

    /**
     * 親行（polls）を排他ロックして読む。人数・選択肢の上限判定、投票、イベント採番はすべてこのロックの下で行う。
     * ロックはトランザクションが終わるまで続く。ほかの更新経路も同じ規則で polls をロックして初めて、
     * 「count してから insert」の間に割り込まれない。
     */
    public Optional<Poll> lockById(long id) {
        return jdbc.sql("SELECT * FROM polls WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(PollRepository::map)
                .optional();
    }

    public Optional<Poll> lockByJoinCode(String joinCode) {
        return jdbc.sql("SELECT * FROM polls WHERE join_code = :joinCode FOR UPDATE")
                .param("joinCode", joinCode)
                .query(PollRepository::map)
                .optional();
    }

    /** 手動締切。すでに締切時刻より前なら、締切時刻も「今」に前倒しする。 */
    public int close(long id, Instant now) {
        return jdbc.sql("""
                UPDATE polls SET status = 'CLOSED', closes_at = LEAST(closes_at, :now)
                WHERE id = :id
                """)
                .param("id", id)
                .param("now", Db.ts(now))
                .update();
    }

    /** 子テーブル（選択肢・参加者・票・招待・イベント）は ON DELETE CASCADE で一緒に消える。 */
    public int delete(long id) {
        return jdbc.sql("DELETE FROM polls WHERE id = :id").param("id", id).update();
    }

    /** 削除期限を過ぎた投票を、最大 limit 件だけ消す。長いトランザクションを避けるため件数を区切る。 */
    public int deleteExpiredBatch(Instant now, int limit) {
        return jdbc.sql("""
                DELETE FROM polls WHERE id IN (
                    SELECT id FROM polls WHERE delete_after <= :now ORDER BY delete_after LIMIT :limit)
                """)
                .param("now", Db.ts(now))
                .param("limit", limit)
                .update();
    }
}
