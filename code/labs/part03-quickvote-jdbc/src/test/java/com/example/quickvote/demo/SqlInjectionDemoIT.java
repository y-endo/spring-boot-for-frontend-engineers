package com.example.quickvote.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.quickvote.poll.PollRepository;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第19章: SQL インジェクションを実際に通す。
 * 「悪い例」は main には置かない。テストの中にだけ、わざと危険な書き方をしてある。
 */
class SqlInjectionDemoIT extends AbstractIntegrationTest {

    @Autowired
    PollRepository polls;

    /** 悪い例: 入力を文字列連結で SQL に埋め込む。 */
    private List<String> findTitlesByJoinCodeBad(String code) {
        String sql = "SELECT title FROM polls WHERE join_code = '" + code + "'";
        System.out.println("[demo] 実行されるSQL: " + sql);
        return jdbc.sql(sql).query(String.class).list();
    }

    /** 良い例: 値は名前付きパラメータ（バインド変数）で渡す。 */
    private List<String> findTitlesByJoinCodeGood(String code) {
        return jdbc.sql("SELECT title FROM polls WHERE join_code = :code")
                .param("code", code)
                .query(String.class)
                .list();
    }

    private void seed() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        polls.insert(UUID.randomUUID(), "社外秘の投票A", "ABCDEFGH", false, now.plusSeconds(600), now);
        polls.insert(UUID.randomUUID(), "社外秘の投票B", "ABCDEFGJ", false, now.plusSeconds(600), now);
    }

    @Test
    void 正しい入力なら_どちらも1件だけ返る() {
        seed();

        assertThat(findTitlesByJoinCodeBad("ABCDEFGH")).containsExactly("社外秘の投票A");
        assertThat(findTitlesByJoinCodeGood("ABCDEFGH")).containsExactly("社外秘の投票A");
    }

    @Test
    void 細工した入力で_連結版は全件を返してしまう() {
        seed();
        String attack = "x' OR '1'='1";

        List<String> leaked = findTitlesByJoinCodeBad(attack);
        List<String> safe = findTitlesByJoinCodeGood(attack);

        System.out.println("[demo] 連結版の結果: " + leaked);
        System.out.println("[demo] バインド版の結果: " + safe);
        assertThat(leaked).hasSize(2);
        assertThat(safe).isEmpty();
    }

    @Test
    void UNIONで別の表の中身まで読まれる() {
        seed();
        jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) VALUES (1, 4242, 'HOST', now())")
                .update();
        String attack = "x' UNION SELECT 'visitor=' || visitor_id || ' role=' || role FROM participants --";

        List<String> leaked = findTitlesByJoinCodeBad(attack);

        System.out.println("[demo] UNION の結果: " + leaked);
        assertThat(leaked).contains("visitor=4242 role=HOST");
        assertThat(findTitlesByJoinCodeGood(attack)).isEmpty();
    }
}
