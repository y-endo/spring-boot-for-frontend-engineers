package com.example.jpalab.support;

import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 実験テストの共通部品。実 PostgreSQL（Testcontainers）・テーブルの初期化・SQL本数の計測。 */
@SpringBootTest
@Import(PostgresConfig.class)
@ExtendWith(OutputCaptureExtension.class)
public abstract class AbstractLabTest {

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected EntityManagerFactory emf;

    @Autowired
    protected PlatformTransactionManager txManager;

    protected TransactionTemplate tx;
    protected TransactionTemplate txNew;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE polls RESTART IDENTITY CASCADE").update();
        tx = new TransactionTemplate(txManager);
        txNew = new TransactionTemplate(txManager);
        txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 投票 polls 件 × 選択肢 optionsPerPoll 件 × 各選択肢に votesPerOption 票。JPA を通さず SQL で一気に入れる。 */
    protected void seed(int polls, int optionsPerPoll, int votesPerOption) {
        jdbc.sql("""
                INSERT INTO polls (title, status, closes_at)
                SELECT 'poll-' || g, 'OPEN', now() + interval '1 day' FROM generate_series(1, :n) g
                """).param("n", polls).update();
        jdbc.sql("""
                INSERT INTO options (poll_id, label)
                SELECT p.id, 'option-' || k FROM polls p, generate_series(1, :m) k ORDER BY p.id, k
                """).param("m", optionsPerPoll).update();
        if (votesPerOption > 0) {
            jdbc.sql("""
                    INSERT INTO votes (poll_id, option_id, participant_id, voted_at)
                    SELECT o.poll_id, o.id,
                           (row_number() OVER (PARTITION BY o.poll_id ORDER BY o.id) - 1) * :v + g, now()
                    FROM options o, generate_series(1, :v) g
                    """).param("v", votesPerOption).update();
        }
    }

    protected Statistics statistics() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    /** action の間に Hibernate が JDBC に渡した SQL（PreparedStatement）の本数を数える。 */
    protected <T> Measured<T> measure(Supplier<T> action) {
        Statistics statistics = statistics();
        statistics.clear();
        T result = action.get();
        return new Measured<>(result, statistics.getPrepareStatementCount());
    }

    protected Measured<Void> measure(Runnable action) {
        return measure(() -> {
            action.run();
            return null;
        });
    }

    public record Measured<T>(T result, long sqlCount) {
    }

    private int outputMark;

    /** ここまでのログは見ない、という目印を付ける（以降に出たログだけを sqlLines などで取り出す）。 */
    protected void mark(CapturedOutput output) {
        outputMark = output.getOut().length();
    }

    private java.util.stream.Stream<String> linesSinceMark(CapturedOutput output) {
        return output.getOut().substring(outputMark).lines();
    }

    /** ログ（org.hibernate.SQL）に出た SQL を、出た順に取り出す。ログの先頭の時刻などは取り除く。 */
    protected List<String> sqlLines(CapturedOutput output) {
        return linesSinceMark(output)
                .filter(line -> line.contains("org.hibernate.SQL"))
                .map(line -> line.substring(line.indexOf(" : ") + 3))
                .toList();
    }

    /** 実験の目印（"lab" ロガー）と SQL を、出た順に並べて取り出す。 */
    protected List<String> labAndSqlLines(CapturedOutput output) {
        return linesSinceMark(output)
                .filter(line -> line.contains("org.hibernate.SQL") || line.contains(" lab "))
                .map(line -> line.substring(line.indexOf(" : ") + 3))
                .toList();
    }
}
