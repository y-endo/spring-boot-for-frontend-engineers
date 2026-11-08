package com.example.quickvote.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.quickvote.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 実PostgreSQL（Testcontainers）を使う統合テストの土台。
 * 同じ設定のテストクラスは ApplicationContext（とコンテナ）を使い回すので、2つ目以降は速い。
 * その代わり、DBの中身もテストクラスをまたいで残る。そこで、テストごとに全テーブルを空にして、
 * 時計も「今」に戻す。テスト同士は、実行順に関係なく独立する（第21章）。
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestClockConfig.class })
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void resetState() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // CASCADE で、polls を参照するテーブル（選択肢・参加者・票）もまとめて空になる。
        // RESTART IDENTITY で、identity の採番も 1 に戻る（テストが ID に依存しても毎回同じ）。
        jdbc.sql("TRUNCATE polls RESTART IDENTITY CASCADE").update();
    }
}
