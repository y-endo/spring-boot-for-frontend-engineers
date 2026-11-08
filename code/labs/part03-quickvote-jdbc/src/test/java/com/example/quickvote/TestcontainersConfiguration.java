package com.example.quickvote;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/**
 * テスト用のPostgreSQLコンテナ。本番と同じ種類・同じメジャーバージョン（18）のDBでテストする。
 * <p>
 * @ServiceConnection を付けると、Spring Boot がコンテナの接続先（ホスト・ポート・ユーザー）を
 * 読み取って、DataSource（と Flyway）に自動で渡す。spring.datasource.url などの設定は要らない。
 * コンテナはこの Bean と同じ寿命（ApplicationContext が生きている間）で、同じ設定のテストの間は使い回される。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

}
