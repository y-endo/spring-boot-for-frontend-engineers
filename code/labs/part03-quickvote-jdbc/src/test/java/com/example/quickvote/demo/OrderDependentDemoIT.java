package com.example.quickvote.demo;

import java.time.Instant;
import java.util.UUID;

import com.example.quickvote.TestcontainersConfiguration;
import com.example.quickvote.poll.PollRepository;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第21章: わざと壊れるテスト（データ独立性がないとどうなるか）。普段は動かさない。
 * 動かすには環境変数 QV_DEMO_BROKEN=1 を付ける。2つ目のテストは、単独で動かすと通り、1つ目の後だと落ちる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "QV_DEMO_BROKEN", matches = "1")
class OrderDependentDemoIT {

    @Autowired
    PollRepository polls;

    @Autowired
    JdbcClient jdbc;

    @Test
    @Order(1)
    void 投票を1件作る() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        polls.insert(UUID.randomUUID(), "1つ目のテストが作った投票", "ABCDEFGH", false, now.plusSeconds(600), now);

        assertThat(count()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void 投票を1件作ると全部で1件になるはず() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        polls.insert(UUID.randomUUID(), "2つ目のテストが作った投票", "ABCDEFGJ", false, now.plusSeconds(600), now);

        assertThat(count()).isEqualTo(1); // 前のテストが残した行があると、2件になって落ちる
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM polls").query(Long.class).single();
    }
}
