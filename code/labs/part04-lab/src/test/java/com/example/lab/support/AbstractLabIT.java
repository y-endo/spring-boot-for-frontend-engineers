package com.example.lab.support;

import java.util.ArrayList;
import java.util.List;

import com.example.lab.LabRepository;

import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 実PostgreSQL 18（Testcontainers）を使う統合テストの土台。QuickVote 本体の AbstractIntegrationTest と同じ考え方。
 * テストごとに表を空にするので、テスト同士は独立している。
 */
// 20スレッドを同時に待たせるテストがあるので、プールを広げる（既定の10本だと、接続を待つスレッドが先に進めず、ラッチが揃わない）。
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=30")
@Import(AbstractLabIT.Containers.class)
public abstract class AbstractLabIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
        }
    }

    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected LabRepository repo;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE polls, visitors, lab_audit, lab_memos RESTART IDENTITY CASCADE").update();
    }

    /** 参加コードに使える31種の文字（0/O/1/I/L を除く）。 */
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private int codeSeq = 0;

    /** 参加者が members 人（主催者を含む）の投票を作る。戻り値は poll の id。 */
    protected long pollWithMembers(int members) {
        int n = codeSeq++;
        String code = "TESTTE" + CODE_CHARS.charAt(n / 31 % 31) + CODE_CHARS.charAt(n % 31);
        long pollId = repo.insertPoll("テスト", code);
        repo.insertParticipant(pollId, repo.insertVisitor(), "HOST");
        for (int i = 1; i < members; i++) {
            repo.insertParticipant(pollId, repo.insertVisitor(), "MEMBER");
        }
        return pollId;
    }

    protected List<Long> newVisitors(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(repo.insertVisitor());
        }
        return ids;
    }
}
