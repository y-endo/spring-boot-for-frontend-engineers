package com.example.quickvote.poll;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.quickvote.common.Db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 第3部の Repository。第2部のメモリ版（Map）と、メソッド名・引数・戻り値は同じ。中身だけ SQL に替わった。
 * <p>
 * この時点の保証: 各メソッドは1本のSQL＝1回の自動コミット。複数のメソッドをまたいだ「全部成功か全部なしか」は
 * まだ守られていない（第22章）。「数えてから足す」の間に割り込まれる問題も残っている（第25章）。
 */
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
                0L); // polls.last_event_seq は第37章（イベント履歴）で足す列。それまでは常に 0
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

    public Optional<Poll> findByPublicId(UUID publicId) {
        return jdbc.sql("SELECT * FROM polls WHERE public_id = :publicId")
                .param("publicId", publicId)
                .query(PollRepository::map)
                .optional();
    }

    /** 完成形ではここが lockByJoinCode（SELECT ... FOR UPDATE）になる。第25章で、そのように書き換える。 */
    public Optional<Poll> findByJoinCode(String joinCode) {
        return jdbc.sql("SELECT * FROM polls WHERE join_code = :joinCode")
                .param("joinCode", joinCode)
                .query(PollRepository::map)
                .optional();
    }
}
