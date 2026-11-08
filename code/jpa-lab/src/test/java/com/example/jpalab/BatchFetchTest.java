package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.jpalab.poll.PollService;
import com.example.jpalab.poll.PollView;
import com.example.jpalab.support.AbstractLabTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

/** 第40章: 遅延ロードのままでも、まとめて取らせる設定（バッチフェッチ）を入れるとSQLが減る。 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=50")
class BatchFetchTest extends AbstractLabTest {

    @Autowired
    PollService service;

    @Test
    void バッチフェッチを入れると_101本が3本になる(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollView>> m = measure(service::listLazy);

        List<String> sql = sqlLines(output);
        System.out.println("[バッチフェッチ(50)] Hibernate統計=" + m.sqlCount() + "本");
        sql.forEach(line -> System.out.println("  " + line.replaceAll("\\?(,\\?)+", "?,…")));
        assertThat(m.result()).hasSize(100);
        assertThat(m.sqlCount()).isEqualTo(3); // 1 + ceil(100 / 50)
    }
}
