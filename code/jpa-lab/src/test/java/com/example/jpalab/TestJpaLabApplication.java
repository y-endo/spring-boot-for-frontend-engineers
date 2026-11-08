package com.example.jpalab;

import com.example.jpalab.support.PostgresConfig;

import org.springframework.boot.SpringApplication;

/** IDE から実行すると、使い捨ての PostgreSQL 18 を起動して、アプリを立ち上げる。 */
public class TestJpaLabApplication {

    public static void main(String[] args) {
        SpringApplication.from(JpaLabApplication::main).with(PostgresConfig.class).run(args);
    }
}
