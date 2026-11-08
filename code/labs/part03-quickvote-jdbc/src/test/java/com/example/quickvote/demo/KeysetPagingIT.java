package com.example.quickvote.demo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.quickvote.common.Db;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第20章: キーセット方式のページング。完成形（code/quickvote）には「投票の一覧」APIがないので、
 * ここでは学習用に、Repository に当たる小さなクラスをテストの中に置いている。
 */
class KeysetPagingIT extends AbstractIntegrationTest {

    /** 一覧の1行。次のページを取るための「しおり」は、最後の行の (createdAt, id) 。 */
    record PollRow(long id, String title, Instant createdAt) {
    }

    /** 次のページを取るための目印。画面やAPIには、この2つを（まとめて不透明な文字列にして）持ち回る。 */
    record Cursor(Instant createdAt, long id) {
    }

    /** 新しい順。同じ時刻の行は id の大きい順で、並びを一意に決める。 */
    private List<PollRow> page(@Nullable Cursor after, int size) {
        String where = after == null ? "" : "WHERE (created_at, id) < (:createdAt, :id)";
        var statement = jdbc.sql("SELECT id, title, created_at FROM polls " + where
                + " ORDER BY created_at DESC, id DESC LIMIT :size").param("size", size);
        if (after != null) {
            statement = statement.param("createdAt", Db.ts(after.createdAt())).param("id", after.id());
        }
        return statement.query(PollRow.class).list();
    }

    private void insertPoll(String title, String code, Instant createdAt) {
        OffsetDateTime at = createdAt.atOffset(ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
                VALUES (:publicId, :title, 'OPEN', :code, false, :at, :at, :at)
                """)
                .param("publicId", UUID.randomUUID()).param("title", title).param("code", code).param("at", at)
                .update();
    }

    @Test
    void 同じ時刻の行がまたがっても_重複も欠落もなく最後まで読める() {
        Instant sameTime = Instant.parse("2030-01-01T00:00:00Z");
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 25; i++) { // 25件が全部同じ時刻
            insertPoll("投票" + i, "AAAAAAA" + alphabet.charAt(i), sameTime);
        }

        List<Long> seen = new ArrayList<>();
        Cursor cursor = null;
        while (true) {
            List<PollRow> rows = page(cursor, 10);
            rows.forEach(r -> seen.add(r.id()));
            if (rows.size() < 10) {
                break;
            }
            PollRow last = rows.getLast();
            cursor = new Cursor(last.createdAt(), last.id());
        }

        assertThat(seen).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    void 読んでいる途中で新しい投票が増えても_次のページは続きから() {
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 12; i++) {
            insertPoll("投票" + i, "BBBBBBB" + alphabet.charAt(i), base.plusSeconds(i));
        }

        List<PollRow> first = page(null, 5);
        insertPoll("割り込み", "CCCCCCCC", base.plusSeconds(100)); // いちばん新しい投票が、読書の途中で増えた
        PollRow last = first.getLast();
        List<PollRow> second = page(new Cursor(last.createdAt(), last.id()), 5);

        assertThat(second).extracting(PollRow::title).doesNotContainAnyElementsOf(first.stream().map(PollRow::title).toList());
        assertThat(second.getFirst().createdAt()).isBefore(last.createdAt());
    }
}
