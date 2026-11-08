package com.example.quickvote;

import java.util.List;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第21章: テストのDBは、本番と同じ Flyway のマイグレーションで作られている。
 * Testcontainers の空のPostgreSQLにアプリが接続すると、Flyway が V1 から順に流す。
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Test
    void テスト用DBにも_本番と同じマイグレーションが全部適用されている() {
        List<String> applied = jdbc.sql("SELECT version || ' ' || description FROM flyway_schema_history "
                + "WHERE success ORDER BY installed_rank").query(String.class).list();

        assertThat(applied).containsExactly("1 create polls and options", "2 create participants and votes");
    }

    /** PostgreSQL 18 では NOT NULL も pg_constraint に載る（contype = 'n'）ので、外して比べる。 */
    @Test
    void 制約もマイグレーションの通りに存在する() {
        List<String> constraints = jdbc.sql("SELECT conname FROM pg_constraint WHERE conrelid = 'votes'::regclass "
                + "AND contype <> 'n' ORDER BY conname").query(String.class).list();

        assertThat(constraints).containsExactly("votes_option_fkey", "votes_participant_fkey",
                "votes_poll_participant_key");
    }
}
