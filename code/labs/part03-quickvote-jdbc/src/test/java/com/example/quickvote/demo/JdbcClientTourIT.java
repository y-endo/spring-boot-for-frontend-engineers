package com.example.quickvote.demo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.quickvote.poll.PollRepository;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 第19章: JdbcClient の使い方を一通り試す。出力は [demo] で始まる行。 */
class JdbcClientTourIT extends AbstractIntegrationTest {

    @Autowired
    PollRepository polls;

    /** 行をそのまま受ける record。列名 join_code → joinCode のように、スネークケースをキャメルケースに読み替えてくれる。 */
    record PollRow(long id, String title, String status, String joinCode, boolean allowMemberOptions) {
    }

    /** Instant を含む record。timestamptz 列を Instant に読めるか試す。 */
    record PollWithTime(long id, String title, Instant createdAt) {
    }

    /** パラメータをまとめた record。:title のような名前付きパラメータに、同名のコンポーネントが入る。 */
    record NewOption(long pollId, String label, long createdBy) {
    }

    private void seed() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        polls.insert(UUID.randomUUID(), "ランチ", "ABCDEFGH", true, now.plusSeconds(600), now);
        polls.insert(UUID.randomUUID(), "飲み会", "ABCDEFGJ", false, now.plusSeconds(600), now);
    }

    @Test
    void 列名をキャメルケースに読み替えて_recordにそのまま入る() {
        seed();

        List<PollRow> rows = jdbc.sql("SELECT id, title, status, join_code, allow_member_options FROM polls ORDER BY id")
                .query(PollRow.class)
                .list();

        System.out.println("[demo] " + rows);
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().joinCode()).isEqualTo("ABCDEFGH");
        assertThat(rows.getFirst().allowMemberOptions()).isTrue();
    }

    @Test
    void timestamptz列はInstantのrecordにも入る() {
        seed();

        PollWithTime row = jdbc.sql("SELECT id, title, created_at FROM polls WHERE id = 1")
                .query(PollWithTime.class)
                .single();

        System.out.println("[demo] " + row);
        assertThat(row.createdAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void 結果の列がrecordに足りないと例外になる() {
        seed();

        try {
            jdbc.sql("SELECT id, title FROM polls WHERE id = 1").query(PollWithTime.class).single();
        } catch (Exception e) {
            System.out.println("[demo] 列が足りないとき: " + e.getClass().getSimpleName() + " / " + e.getMessage());
        }
    }

    @Test
    void IN句には_リストをそのまま渡せる() {
        seed();

        List<String> titles = jdbc.sql("SELECT title FROM polls WHERE id IN (:ids) ORDER BY id")
                .param("ids", List.of(1L, 2L, 99L))
                .query(String.class)
                .list();

        assertThat(titles).containsExactly("ランチ", "飲み会");
    }

    @Test
    void Mapでもrecordでも_まとめて渡せる() {
        seed();
        jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) VALUES (1, 7, 'HOST', now())").update();

        int byMap = jdbc.sql("INSERT INTO options (poll_id, label, created_by, created_at) VALUES (:pollId, :label, :createdBy, now())")
                .params(Map.of("pollId", 1L, "label", "カレー", "createdBy", 1L))
                .update();
        int byRecord = jdbc.sql("INSERT INTO options (poll_id, label, created_by, created_at) VALUES (:pollId, :label, :createdBy, now())")
                .paramSource(new NewOption(1L, "そば", 1L))
                .update();

        assertThat(byMap).isEqualTo(1);
        assertThat(byRecord).isEqualTo(1);
    }

    @Test
    void 同じ名前付きパラメータは_何度書いても1回の渡しで足りる() {
        seed();

        List<Long> ids = jdbc.sql("SELECT id FROM polls WHERE id = :n OR id = :n + 1 ORDER BY id")
                .param("n", 1L)
                .query(Long.class)
                .list();

        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    void Instantを直接バインドできるか() {
        seed();
        Instant at = Instant.parse("2030-01-01T00:00:00Z");

        try {
            List<Long> ids = jdbc.sql("SELECT id FROM polls WHERE created_at = :at").param("at", at).query(Long.class).list();
            System.out.println("[demo] Instant を直接バインド: 成功 " + ids);
        } catch (Exception e) {
            System.out.println("[demo] Instant を直接バインド: " + e.getClass().getSimpleName() + " / 原因: "
                    + org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        }
        List<Long> viaOffset = jdbc.sql("SELECT id FROM polls WHERE created_at = :at")
                .param("at", at.atOffset(java.time.ZoneOffset.UTC)).query(Long.class).list();
        System.out.println("[demo] OffsetDateTime に直してバインド: " + viaOffset);
    }
}
