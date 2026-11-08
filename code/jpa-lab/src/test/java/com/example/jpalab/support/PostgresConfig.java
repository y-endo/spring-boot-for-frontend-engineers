package com.example.jpalab.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 実験用の PostgreSQL 18。テストクラスごとに ApplicationContext が作られても、コンテナは JVM で1つだけ起動して使い回す。
 * 接続先は @ServiceConnection が datasource に自動で渡す（spring.datasource.* を書かなくてよい）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresConfig {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
