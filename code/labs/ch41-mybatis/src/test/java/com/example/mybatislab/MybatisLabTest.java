package com.example.mybatislab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 第41章のコラム用: MyBatis（XMLにSQLを書く方式）を、同じスキーマで実際に動かして観察する。 */
@SpringBootTest
@Import(MybatisLabTest.Postgres.class)
@ExtendWith(OutputCaptureExtension.class)
class MybatisLabTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        }
    }

    @Autowired
    PollMapper mapper;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("TRUNCATE polls RESTART IDENTITY CASCADE").update();
        jdbc.sql("""
                INSERT INTO polls (title, status, closes_at)
                SELECT 'poll-' || g, 'OPEN', now() + interval '1 day' FROM generate_series(1, 100) g
                """).update();
        jdbc.sql("""
                INSERT INTO options (poll_id, label)
                SELECT p.id, 'option-' || k FROM polls p, generate_series(1, 3) k ORDER BY p.id, k
                """).update();
    }

    private static List<String> preparing(CapturedOutput output, int fromIndex) {
        return output.getOut().substring(fromIndex).lines()
                .filter(l -> l.contains("==>  Preparing:"))
                .map(l -> l.substring(l.indexOf("Preparing:") + 11).strip())
                .toList();
    }

    @Test
    void XMLのSQLが_Javaのメソッドとして呼べる(CapturedOutput output) {
        mapper.upsertVote(1, 1, 1);
        mapper.upsertVote(1, 2, 1);
        mapper.upsertVote(1, 3, 2);
        int from = output.getOut().length();

        List<OptionCount> counts = mapper.findWithVoteCounts(1);

        output.getOut().substring(from).lines().filter(l -> l.contains("==>")).forEach(l ->
                System.out.println("[MyBatis] " + l.substring(l.indexOf("==>"))));
        System.out.println("[MyBatis] 結果: " + counts);
        assertThat(counts).containsExactly(new OptionCount(1, "option-1", 2), new OptionCount(2, "option-2", 1),
                new OptionCount(3, "option-3", 0));
    }

    @Test
    void upsert_の更新件数は_QuickVoteと同じ() {
        int first = mapper.upsertVote(1, 1, 1);
        int changed = mapper.upsertVote(1, 1, 2);
        int same = mapper.upsertVote(1, 1, 2);
        System.out.println("[MyBatis] 更新件数: 初回=" + first + " 変更=" + changed + " 同じ=" + same);
        assertThat(List.of(first, changed, same)).containsExactly(1, 1, 0);
    }

    @Test
    void MyBatisにもN_plus_1がある_入れ子のselect(CapturedOutput output) {
        int from = output.getOut().length();

        List<PollWithOptions> polls = mapper.listWithOptionsNested();

        List<String> sql = preparing(output, from);
        System.out.println("[MyBatis 入れ子select] SQL " + sql.size() + "本");
        sql.stream().limit(3).forEach(s -> System.out.println("  " + s));
        assertThat(polls).hasSize(100);
        assertThat(polls.getFirst().getOptions()).hasSize(3);
        assertThat(sql).hasSize(101);
    }

    @Test
    void JOINして_collectionに詰め直せば1本(CapturedOutput output) {
        int from = output.getOut().length();

        List<PollWithOptions> polls = mapper.listWithOptionsJoin();

        List<String> sql = preparing(output, from);
        System.out.println("[MyBatis JOIN+collection] SQL " + sql.size() + "本");
        assertThat(polls).hasSize(100);
        assertThat(polls.getFirst().getOptions()).extracting(OptionRow::getLabel)
                .containsExactly("option-1", "option-2", "option-3");
        assertThat(sql).hasSize(1);
    }

    @Test
    void 動的SQL_条件が無ければWHEREごと消える(CapturedOutput output) {
        int from = output.getOut().length();

        mapper.search(null, null, "id");
        mapper.search("OPEN", "poll-1", "id");

        List<String> sql = preparing(output, from);
        sql.forEach(s -> System.out.println("[MyBatis 動的SQL] " + s));
        assertThat(sql.get(0)).doesNotContain("WHERE");
        assertThat(sql.get(1)).contains("WHERE status = ? AND title LIKE concat('%', ?, '%')");
    }

    @Test
    void 壊してみる_ORDER_BYにドル記法を使うと文字列がそのまま埋め込まれる(CapturedOutput output) {
        int from = output.getOut().length();

        // ${orderBy} は SQL 文にそのまま入る。「id」ではなく別の式を渡せてしまう。
        List<PollRow> rows = mapper.search(null, null, "id DESC");

        List<String> sql = preparing(output, from);
        sql.forEach(s -> System.out.println("[MyBatis ${}] " + s));
        assertThat(rows.getFirst().getId()).isEqualTo(100L);
        assertThat(sql.getFirst()).endsWith("ORDER BY id DESC");
    }
}
