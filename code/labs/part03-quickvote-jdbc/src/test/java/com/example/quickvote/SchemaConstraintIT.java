package com.example.quickvote;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.example.quickvote.poll.CreatePollRequest;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.poll.PollService;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 制約は最終防衛線。アプリのコード（if）を通さず SQL を直接実行して、DB 自身が不正なデータを拒否することを確かめる。
 * （完成形 code/quickvote の SchemaConstraintIT から、visitors 表に関わる部分を除いたもの）
 */
class SchemaConstraintIT extends AbstractIntegrationTest {

    @Autowired
    PollService pollService;

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private void insertPoll(String title, String joinCode, String status) {
        jdbc.sql("""
                INSERT INTO polls (public_id, title, status, join_code, allow_member_options, closes_at, delete_after, created_at)
                VALUES (:publicId, :title, :status, :joinCode, false, :closes, :deleteAfter, :now)
                """)
                .param("publicId", UUID.randomUUID()).param("title", title).param("status", status)
                .param("joinCode", joinCode).param("closes", now().plusHours(1))
                .param("deleteAfter", now().plusDays(7)).param("now", now()).update();
    }

    private PollDetail createPoll(long visitorId, String... labels) {
        return pollService.create(visitorId, new CreatePollRequest("テスト投票", 10, false, List.of(labels)));
    }

    @Test
    void 参加コードは重複できない() {
        insertPoll("a", "ABCDEFGH", "OPEN");

        assertThatThrownBy(() -> insertPoll("b", "ABCDEFGH", "OPEN")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 参加コードの形式が違えば拒否される() {
        assertThatThrownBy(() -> insertPoll("a", "abcdefgh", "OPEN")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPoll("a", "ABCDEFG0", "OPEN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 空のタイトルと不正な状態は拒否される() {
        assertThatThrownBy(() -> insertPoll("", "ABCDEFGH", "OPEN")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPoll("a", "ABCDEFGH", "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 同じ訪問者は同じ投票に二重に参加できない() {
        createPoll(7L, "A");
        long pollId = jdbc.sql("SELECT id FROM polls").query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) "
                + "VALUES (:p, :v, 'MEMBER', :now)").param("p", pollId).param("v", 7L).param("now", now()).update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 同じ参加者の票は2行にできない() {
        PollDetail poll = createPoll(7L, "A", "B");
        long pollId = jdbc.sql("SELECT id FROM polls").query(Long.class).single();
        long participantId = jdbc.sql("SELECT id FROM participants").query(Long.class).single();
        jdbc.sql("INSERT INTO votes (poll_id, participant_id, option_id, voted_at) VALUES (:p, :pt, :o, :now)")
                .param("p", pollId).param("pt", participantId).param("o", poll.options().get(0).id()).param("now", now())
                .update();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO votes (poll_id, participant_id, option_id, voted_at) "
                + "VALUES (:p, :pt, :o, :now)").param("p", pollId).param("pt", participantId)
                .param("o", poll.options().get(1).id()).param("now", now()).update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 別の投票の選択肢には投票できない_複合外部キー() {
        PollDetail pollA = createPoll(7L, "A");
        PollDetail pollB = createPoll(7L, "B");
        long pollIdA = jdbc.sql("SELECT id FROM polls WHERE public_id = :id").param("id", pollA.id())
                .query(Long.class).single();
        long participantA = jdbc.sql("SELECT id FROM participants WHERE poll_id = :p").param("p", pollIdA)
                .query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO votes (poll_id, participant_id, option_id, voted_at) "
                + "VALUES (:p, :pt, :o, :now)").param("p", pollIdA).param("pt", participantA)
                .param("o", pollB.options().get(0).id()).param("now", now()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 参加者は51人目も入ってしまう_複数行にまたがる条件はCHECKでは守れない() {
        createPoll(1L, "A");
        long pollId = jdbc.sql("SELECT id FROM polls").query(Long.class).single();

        for (long visitor = 2; visitor <= 60; visitor++) { // 主催者を含めて60人
            jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) VALUES (:p, :v, 'MEMBER', :now)")
                    .param("p", pollId).param("v", visitor).param("now", now()).update();
        }

        // 仕様は50人まで。DBの制約は止めてくれない。止めるのはアプリ（第4部で同時実行にも耐えさせる）
        assertThat(jdbc.sql("SELECT count(*) FROM participants").query(Long.class).single()).isEqualTo(60);
    }

    @Test
    void 投票を削除すると子テーブルがすべて連鎖して消える() {
        createPoll(1L, "A", "B");

        jdbc.sql("DELETE FROM polls").update();

        for (String table : new String[] { "polls", "options", "participants" }) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single()).as(table).isZero();
        }
    }
}
