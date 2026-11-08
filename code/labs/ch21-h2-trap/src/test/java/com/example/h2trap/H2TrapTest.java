package com.example.h2trap;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第21章: 「H2 で通ったから安心」が危ない理由を、実際に H2 で動かして確かめる。
 * 本線のマイグレーション・SQL を、PostgreSQL 互換モードの H2 にそのまま流す。
 */
class H2TrapTest {

    private static final String MIGRATIONS = "filesystem:../part03-quickvote-jdbc/src/main/resources/db/migration";

    private DriverManagerDataSource newH2(String name) {
        // MODE=PostgreSQL: H2 が PostgreSQL に似せて動く設定。「互換」であって「同じ」ではない
        return new DriverManagerDataSource("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void 本線のマイグレーションはH2では流れない() {
        DriverManagerDataSource ds = newH2("migrate");

        assertThatThrownBy(() -> Flyway.configure().dataSource(ds).locations(MIGRATIONS).load().migrate())
                .satisfies(e -> System.out.println("[demo] Flyway on H2: " + firstLines(e.getMessage())));
    }

    @Test
    void H2用に作り直したスキーマなら流れるが_それはもう本番のスキーマではない() throws SQLException {
        DriverManagerDataSource ds = newH2("handmade");
        JdbcClient jdbc = JdbcClient.create(ds);
        // 「H2 でも動くように」手で書き直した簡易スキーマ。CHECK（正規表現）や複合外部キーなど、本線の制約が抜けている
        jdbc.sql("""
                CREATE TABLE votes (
                    poll_id bigint NOT NULL, participant_id bigint NOT NULL, option_id bigint NOT NULL,
                    voted_at timestamp with time zone NOT NULL,
                    CONSTRAINT votes_poll_participant_key UNIQUE (poll_id, participant_id))
                """).update();

        // 本線の VoteRepository.upsert と同じ SQL
        String upsert = """
                INSERT INTO votes (poll_id, participant_id, option_id, voted_at)
                VALUES (:pollId, :participantId, :optionId, :now)
                ON CONFLICT (poll_id, participant_id) DO UPDATE
                   SET option_id = EXCLUDED.option_id, voted_at = EXCLUDED.voted_at
                   WHERE votes.option_id <> EXCLUDED.option_id
                """;
        assertThatThrownBy(() -> jdbc.sql(upsert).param("pollId", 1L).param("participantId", 1L).param("optionId", 1L)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC)).update())
                .satisfies(e -> System.out.println("[demo] upsert on H2: "
                        + NestedExceptionUtils.getMostSpecificCause(e).getMessage().replace("\n", " ")));
    }

    @Test
    void 時刻の扱いがPostgreSQLと違う() {
        DriverManagerDataSource ds = newH2("time");
        JdbcClient jdbc = JdbcClient.create(ds);
        jdbc.sql("CREATE TABLE t (id int, at timestamp with time zone NOT NULL)").update();
        // 日本時間 +09:00 で、ナノ秒つきの時刻を書く
        OffsetDateTime jst = OffsetDateTime.parse("2030-01-01T09:00:00.123456789+09:00");
        jdbc.sql("INSERT INTO t VALUES (1, :at)").param("at", jst).update();

        OffsetDateTime back = jdbc.sql("SELECT at FROM t WHERE id = 1").query(OffsetDateTime.class).single();
        Instant instant = jdbc.sql("SELECT at FROM t WHERE id = 1").query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).single();
        System.out.println("[demo] H2 が返した時刻: " + back + "（書いたのは " + jst + "）");
        System.out.println("[demo] H2 の Instant: " + instant);

        assertThat(back.toInstant()).isNotNull();
    }

    private static String firstLines(String message) {
        String[] lines = message.split("\n");
        return String.join(" | ", java.util.Arrays.copyOf(lines, Math.min(lines.length, 9)));
    }
}
