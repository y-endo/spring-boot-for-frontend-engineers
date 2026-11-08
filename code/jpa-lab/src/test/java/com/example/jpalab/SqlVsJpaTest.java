package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.example.jpalab.poll.OptionCount;
import com.example.jpalab.poll.OptionRepository;
import com.example.jpalab.poll.Poll;
import com.example.jpalab.poll.PollRepository;
import com.example.jpalab.poll.PollStatus;
import com.example.jpalab.poll.VoteRepository;
import com.example.jpalab.poll.VoteService;
import com.example.jpalab.support.AbstractLabTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.dao.DataIntegrityViolationException;

/** 第41章: 同じユースケースを JdbcClient（QuickVote）と JPA で書いたときの違い。 */
class SqlVsJpaTest extends AbstractLabTest {

    @Autowired
    PollRepository polls;

    @Autowired
    OptionRepository options;

    @Autowired
    VoteRepository votes;

    @Autowired
    VoteService voteService;

    @Autowired
    javax.sql.DataSource dataSource;

    @PersistenceContext
    EntityManager em;

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    // ------------------------------------------------------------------ ① 選択肢ごとの得票数

    @Test
    void 得票数の集計_QuickVoteのSQLとJPQLは同じ結果を返す(CapturedOutput output) {
        seed(1, 3, 0);
        // 選択肢1に3票、選択肢2に1票、選択肢3に0票
        for (long participant = 1; participant <= 3; participant++) {
            voteService.voteWithNativeUpsert(1L, participant, 1L, NOW);
        }
        voteService.voteWithNativeUpsert(1L, 4L, 2L, NOW);
        mark(output);

        // QuickVote の OptionRepository.findWithVoteCounts と同じSQL（JdbcClient）
        List<OptionCount> byJdbc = jdbc.sql("""
                SELECT o.id, o.label, count(v.participant_id) AS votes
                FROM options o
                LEFT JOIN votes v ON v.option_id = o.id
                WHERE o.poll_id = :pollId
                GROUP BY o.id, o.label
                ORDER BY o.id
                """)
                .param("pollId", 1L)
                .query((rs, n) -> new OptionCount(rs.getLong("id"), rs.getString("label"), rs.getInt("votes")))
                .list();
        List<OptionCount> byJpa = tx.execute(s -> options.findWithVoteCounts(1L));

        System.out.println("[得票数] JdbcClient=" + byJdbc);
        System.out.println("[得票数] JPQL      =" + byJpa);
        System.out.println("[得票数] JPA が発行したSQL ↓");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(byJpa).isEqualTo(byJdbc);
        assertThat(byJpa).extracting(OptionCount::votes).containsExactly(3L, 1L, 0L);
    }

    // ------------------------------------------------------------------ ② 投票（1人1票・上書き）

    @Test
    void 投票のJPA版_初回はINSERT_変更はUPDATE_同じ選択肢ならSQLなし(CapturedOutput output) {
        seed(1, 3, 0);

        mark(output);
        voteService.voteWithJpa(1L, 100L, 1L, NOW);
        System.out.println("[JPA版 投票] ① 初めての投票 ↓");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(sqlLines(output)).hasSize(2);
        assertThat(sqlLines(output).get(0)).startsWith("select v1_0.id");
        assertThat(sqlLines(output).get(1)).startsWith("insert into votes");

        mark(output);
        voteService.voteWithJpa(1L, 100L, 2L, NOW.plusSeconds(1));
        System.out.println("[JPA版 投票] ② 別の選択肢へ変更 ↓");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(sqlLines(output)).hasSize(2);
        assertThat(sqlLines(output).get(1)).startsWith("update votes set");

        mark(output);
        voteService.voteWithJpa(1L, 100L, 2L, NOW.plusSeconds(2));
        System.out.println("[JPA版 投票] ③ 同じ選択肢へ再投票（時刻だけ違う） ↓");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
    }

    @Test
    void 投票のネイティブ版_QuickVoteと同じON_CONFLICT_更新件数も同じ(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        int first = voteService.voteWithNativeUpsert(1L, 100L, 1L, NOW);
        int changed = voteService.voteWithNativeUpsert(1L, 100L, 2L, NOW);
        int same = voteService.voteWithNativeUpsert(1L, 100L, 2L, NOW);

        System.out.println("[ネイティブ版 投票] 更新件数: 初回=" + first + " 変更=" + changed + " 同じ選択肢=" + same);
        sqlLines(output).stream().limit(1).forEach(line -> System.out.println("  " + line.strip().replaceAll("\\s+", " ")));
        assertThat(List.of(first, changed, same)).containsExactly(1, 1, 0);
    }

    @Test
    void 投票のJPA版は_確認してから挿入_なので_同時に初投票すると一意制約違反になる() {
        seed(1, 3, 0);

        assertThatThrownBy(() -> tx.executeWithoutResult(a -> {
            // A: 「この人の票はまだない」と確認した
            assertThat(votes.findByPollIdAndParticipantId(1L, 100L)).isEmpty();
            // B: 同じ人の別リクエストが先に INSERT してコミットした
            txNew.executeWithoutResult(b -> voteService.voteWithJpa(1L, 100L, 1L, NOW));
            // A: 続きの INSERT
            votes.saveAndFlush(new com.example.jpalab.poll.Vote(1L, 100L, options.getReferenceById(2L), NOW));
        })).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> System.out.println("[確認してから挿入] " + e.getClass().getSimpleName() + " ← "
                        + e.getCause().getClass().getSimpleName()));
        // 1人1票は、アプリのifではなく UNIQUE 制約が守った
        assertThat(jdbc.sql("SELECT count(*) FROM votes").query(Integer.class).single()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ ③ 親行ロック

    @Test
    void 親行ロック_QuickVoteはFOR_UPDATE_JPAのPESSIMISTIC_WRITEは何を出すか(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        voteService.lockPoll(1L);

        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(lines.get(1)).startsWith("select").contains("for no key update");
    }

    @Test
    void 親行ロックの強さ_FOR_UPDATEは子行のINSERTを待たせ_FOR_NO_KEY_UPDATEは待たせない() throws Exception {
        seed(1, 1, 0);
        String forUpdate = lockedInsertResult("FOR UPDATE");
        String noKeyUpdate = lockedInsertResult("FOR NO KEY UPDATE");
        System.out.println("[ロックの強さ] polls を FOR UPDATE でロック中に options へ INSERT → " + forUpdate);
        System.out.println("[ロックの強さ] polls を FOR NO KEY UPDATE でロック中に options へ INSERT → " + noKeyUpdate);
        assertThat(forUpdate).startsWith("待たされた");
        assertThat(noKeyUpdate).isEqualTo("すぐ成功");
    }

    /** 1本目の接続が polls の行をロックしたまま、2本目の接続が子表（options）に INSERT する。 */
    private String lockedInsertResult(String lockClause) throws Exception {
        try (var locker = dataSource.getConnection(); var other = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            other.setAutoCommit(true);
            try (var st = locker.createStatement()) {
                st.execute("SELECT id FROM polls WHERE id = 1 " + lockClause);
            }
            try (var st = other.createStatement()) {
                st.execute("SET lock_timeout = '500ms'");
                st.execute("INSERT INTO options (poll_id, label) VALUES (1, 'x')");
                return "すぐ成功";
            } catch (java.sql.SQLException e) {
                return "待たされた（lock_timeout で中断: " + e.getSQLState() + "）";
            } finally {
                locker.rollback();
            }
        }
    }

    // ------------------------------------------------------------------ ④ 混在させたときの落とし穴

    @Test
    void 混在1_JPAの変更はflushするまでJdbcClientから見えない() {
        seed(1, 3, 0);

        String[] seen = tx.execute(s -> {
            Poll poll = polls.findById(1L).orElseThrow();
            poll.close(Instant.now());
            String beforeFlush = jdbc.sql("SELECT status FROM polls WHERE id = 1").query(String.class).single();
            polls.flush();
            String afterFlush = jdbc.sql("SELECT status FROM polls WHERE id = 1").query(String.class).single();
            return new String[] { beforeFlush, afterFlush };
        });

        System.out.println("[混在1] 同じトランザクション内で JdbcClient が見た status: flush前=" + seen[0] + " / flush後=" + seen[1]);
        assertThat(seen).containsExactly("OPEN", "CLOSED");
    }

    @Test
    void 混在2_SQLで直接更新すると_Entityは古いまま() {
        seed(1, 3, 0);

        String[] seen = tx.execute(s -> {
            Poll poll = polls.findById(1L).orElseThrow(); // 永続コンテキストに載る（OPEN）
            jdbc.sql("UPDATE polls SET status = 'CLOSED' WHERE id = 1").update(); // JPA を通さない更新
            String entityStatus = polls.findById(1L).orElseThrow().getStatus().name(); // SQLは出ず、カゴの中身が返る
            em.refresh(poll);
            String afterRefresh = poll.getStatus().name();
            return new String[] { entityStatus, afterRefresh };
        });

        System.out.println("[混在2] SQLで CLOSED に更新したあと Entity が見せた status: そのまま=" + seen[0] + " / refresh後=" + seen[1]);
        assertThat(seen).containsExactly("OPEN", "CLOSED");
    }

    @Test
    void 混在3_バルク更新のJPQLも_Entityは古いまま(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        String[] seen = tx.execute(s -> {
            Poll poll = polls.findById(1L).orElseThrow();
            int updated = polls.closeInBulk(1L);
            return new String[] { String.valueOf(updated), poll.getStatus().name(),
                    jdbc.sql("SELECT status FROM polls WHERE id = 1").query(String.class).single() };
        });

        sqlLines(output).forEach(line -> System.out.println("  " + line));
        System.out.println("[混在3] バルク更新: 更新件数=" + seen[0] + " / Entityの status=" + seen[1] + " / DBの status=" + seen[2]);
        assertThat(seen).containsExactly("1", "OPEN", "CLOSED");
        assertThat(polls.findById(1L).orElseThrow().getStatus()).isEqualTo(PollStatus.CLOSED); // 新しい tx なら正しい
    }

    @Test
    void 混在4_ネイティブクエリの前にも_変更はflushされる(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        Object status = tx.execute(s -> {
            Poll poll = polls.findById(1L).orElseThrow();
            poll.close(Instant.now());
            return em.createNativeQuery("select status from polls where id = 1").getSingleResult();
        });

        sqlLines(output).forEach(line -> System.out.println("  " + line));
        System.out.println("[混在4] ネイティブクエリが返した status=" + status);
        assertThat(status).isEqualTo("CLOSED");
    }
}
