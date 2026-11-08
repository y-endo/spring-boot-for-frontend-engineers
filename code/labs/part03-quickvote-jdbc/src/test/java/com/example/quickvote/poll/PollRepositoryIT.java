package com.example.quickvote.poll;

import java.time.Instant;
import java.util.UUID;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 第19章: JdbcClient の戻り値・更新件数・例外の変換を、実PostgreSQLで観察する。 */
class PollRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    PollRepository polls;

    private long insert(String code) {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        return polls.insert(UUID.randomUUID(), "t", code, false, now.plusSeconds(600), now);
    }

    @Test
    void insertはRETURNINGで採番されたidを返す() {
        assertThat(insert("ABCDEFGH")).isEqualTo(1L); // TRUNCATE ... RESTART IDENTITY で毎回 1 から
        assertThat(insert("ABCDEFGJ")).isEqualTo(2L);
    }

    @Test
    void 参加コードが重複するとDuplicateKeyException() {
        insert("ABCDEFGH");

        assertThatThrownBy(() -> insert("ABCDEFGH")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 見つからないときはOptional_空() {
        assertThat(polls.findById(999)).isEmpty();
    }

    @Test
    void singleは0行でも2行以上でも例外() {
        assertThatThrownBy(() -> jdbc.sql("SELECT id FROM polls").query(Long.class).single())
                .isInstanceOf(EmptyResultDataAccessException.class);
        insert("ABCDEFGH");
        insert("ABCDEFGJ");
        assertThatThrownBy(() -> jdbc.sql("SELECT id FROM polls").query(Long.class).single())
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    @Test
    void updateは更新された行数を返す() {
        insert("ABCDEFGH");
        insert("ABCDEFGJ");

        int hit = jdbc.sql("UPDATE polls SET status = 'CLOSED' WHERE join_code = :c").param("c", "ABCDEFGH").update();
        int miss = jdbc.sql("UPDATE polls SET status = 'CLOSED' WHERE join_code = :c").param("c", "ZZZZZZZZ").update();
        int all = jdbc.sql("UPDATE polls SET status = 'CLOSED'").update();

        assertThat(hit).isEqualTo(1);
        assertThat(miss).isZero();
        assertThat(all).isEqualTo(2);
    }

    @Test
    void 時刻はマイクロ秒で往復する_ナノ秒は丸められる() {
        Instant nanos = Instant.parse("2030-01-01T00:00:00.123456789Z");
        Instant micros = Instant.parse("2030-01-01T00:00:00.123457Z"); // PostgreSQL の timestamptz はマイクロ秒精度
        long id = polls.insert(UUID.randomUUID(), "t", "ABCDEFGH", false, nanos.plusSeconds(600), nanos);

        Poll saved = polls.findById(id).orElseThrow();

        assertThat(saved.createdAt()).isNotEqualTo(nanos);
        assertThat(saved.createdAt()).isEqualTo(micros); // 四捨五入されている
    }
}
