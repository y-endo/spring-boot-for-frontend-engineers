package com.example.quickvote;

import org.springframework.boot.SpringApplication;

/**
 * 開発用: Docker Compose の代わりに Testcontainers の PostgreSQL で本体を起動する。
 * IDE からこの main を実行すると、使い捨てのDBつきでアプリが立ち上がる。
 */
public class TestQuickvoteApplication {

    public static void main(String[] args) {
        SpringApplication.from(QuickvoteApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
