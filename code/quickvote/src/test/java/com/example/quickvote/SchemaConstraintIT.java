package com.example.quickvote;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 制約は最終防衛線。アプリのコード（if）を通さず SQL を直接実行して、DB 自身が不正なデータを拒否することを確かめる。
 */
class SchemaConstraintIT extends AbstractIntegrationTest {

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
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        long pollId = jdbc.sql("SELECT id FROM polls").query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) "
                + "VALUES (:p, :v, 'MEMBER', :now)").param("p", pollId).param("v", host.id()).param("now", now()).update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 同じ参加者の票は2行にできない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B");
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
        TestVisitor host = newVisitor();
        PollDetail pollA = createPoll(host, false, "A");
        PollDetail pollB = createPoll(host, false, "B");
        long pollIdA = jdbc.sql("SELECT id FROM polls WHERE public_id = :id").param("id", pollA.id())
                .query(Long.class).single();
        long participantA = jdbc.sql("SELECT id FROM participants WHERE poll_id = :p").param("p", pollIdA)
                .query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO votes (poll_id, participant_id, option_id, voted_at) "
                + "VALUES (:p, :pt, :o, :now)").param("p", pollIdA).param("pt", participantA)
                .param("o", pollB.options().get(0).id()).param("now", now()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertOption(long pollId, long createdBy, String label) {
        jdbc.sql("INSERT INTO options (poll_id, label, created_by, created_at) VALUES (:p, :l, :c, :now)")
                .param("p", pollId).param("l", label).param("c", createdBy).param("now", now()).update();
    }

    private long dbPollId(PollDetail poll) {
        return jdbc.sql("SELECT id FROM polls WHERE public_id = :id").param("id", poll.id()).query(Long.class).single();
    }

    private long hostParticipantId(long pollId) {
        return jdbc.sql("SELECT id FROM participants WHERE poll_id = :p").param("p", pollId).query(Long.class).single();
    }

    @Test
    void 別の投票の参加者を作成者にした選択肢は拒否される_複合外部キー() {
        PollDetail pollA = createPoll(newVisitor(), false, "A");
        PollDetail pollB = createPoll(newVisitor(), false, "B");
        long pollIdA = dbPollId(pollA);
        long pollIdB = dbPollId(pollB);
        long participantA = hostParticipantId(pollIdA);
        long participantB = hostParticipantId(pollIdB);

        // 投票B の選択肢を、投票A の参加者が作ったことにはできない（V6 より前は通ってしまった）
        assertThatThrownBy(() -> insertOption(pollIdB, participantA, "不正"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 対照: 同じ投票の参加者なら通る
        insertOption(pollIdB, participantB, "正常");
        assertThat(countRows("options")).isEqualTo(3); // 作成時の A・B ＋ 正常
    }

    @Test
    void 投票を削除しても別の投票の選択肢は残る() {
        TestVisitor host1 = newVisitor();
        TestVisitor host2 = newVisitor();
        PollDetail poll1 = createPoll(host1, false, "A1", "A2");
        PollDetail poll2 = createPoll(host2, false, "B1", "B2");
        long pollId1 = dbPollId(poll1);
        long pollId2 = dbPollId(poll2);
        // 投票1の参加者を作成者にして投票2へ選択肢を入れようとする（拒否される）。拒否されなければ、
        // 投票1の削除で投票2の選択肢まで連鎖して消えていた。
        assertThatThrownBy(() -> insertOption(pollId2, hostParticipantId(pollId1), "紛れ込み"))
                .isInstanceOf(DataIntegrityViolationException.class);

        pollService.delete(host1.id(), poll1.id());

        assertThat(jdbc.sql("SELECT count(*) FROM options WHERE poll_id = :p").param("p", pollId1)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT label FROM options WHERE poll_id = :p ORDER BY id").param("p", pollId2)
                .query(String.class).list()).containsExactly("B1", "B2");
    }

    @Test
    void 投票を削除すると子テーブルがすべて連鎖して消える() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        pollService.addOption(host.id(), poll.id(), "k", "B");

        pollService.delete(host.id(), poll.id());

        for (String table : new String[] { "polls", "options", "participants", "poll_events" }) {
            assertThat(countRows(table)).as(table).isZero();
        }
        assertThat(countRows("visitors")).isEqualTo(1); // 訪問者は投票より長く生きる
    }
}
