package com.example.quickvote.poll;

import java.time.Instant;
import java.util.UUID;

import com.example.quickvote.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第21章: Repository だけを確かめる軽いテスト（@JdbcTest）。
 * @JdbcTest は既定で「DataSource を組み込みDB（H2 など）に差し替える」。
 * replace = NONE にして、Testcontainers の PostgreSQL をそのまま使う。Flyway も（@JdbcTest のスライスに含まれて）流れる。
 * Service や Controller は起動しないので、Repository を @Import で足す。
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, PollRepository.class })
class PollRepositorySliceIT {

    @Autowired
    PollRepository polls;

    @Test
    void 保存した投票を読み戻せる() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        UUID publicId = UUID.randomUUID();

        long id = polls.insert(publicId, "ランチ", "ABCDEFGH", true, now.plusSeconds(600), now);

        Poll saved = polls.findById(id).orElseThrow();
        assertThat(saved.publicId()).isEqualTo(publicId);
        assertThat(saved.title()).isEqualTo("ランチ");
        assertThat(saved.status()).isEqualTo(PollStatus.OPEN);
        assertThat(saved.closesAt()).isEqualTo(now.plusSeconds(600));
    }
}
