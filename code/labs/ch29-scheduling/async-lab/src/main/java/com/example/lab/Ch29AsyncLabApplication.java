package com.example.lab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Ch29AsyncLabApplication {

    static final long START = System.currentTimeMillis();

    /** 起動からの経過ミリ秒つきで、スレッド名と一緒に1行出す。 */
    static void say(String message) {
        System.out.printf("t=%5dms [%s] %s%n", System.currentTimeMillis() - START, Thread.currentThread().getName(),
                message);
    }

    public static void main(String[] args) {
        SpringApplication.run(Ch29AsyncLabApplication.class, args);
    }

    /** 実験が終わったら止める（スケジューラのスレッドは止めないとプロセスが終わらない）。 */
    @Bean
    @Order(100)
    ApplicationRunner stopper(ConfigurableApplicationContext context, @Value("${lab.run-seconds}") int seconds) {
        return args -> {
            Thread.sleep(seconds * 1000L);
            System.exit(SpringApplication.exit(context));
        };
    }
}
