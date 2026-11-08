package com.example.lab;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** @Async とトランザクション。呼び出し元のトランザクションは、別スレッドには引き継がれない。 */
@Configuration
@ConditionalOnProperty(name = "lab.mode", havingValue = "async")
class AsyncDemo {

    record MemoCreated(long id) {
    }

    @Service
    static class MemoService {

        private final JdbcClient jdbc;
        private final MemoReader reader;
        private final ApplicationEventPublisher events;

        MemoService(JdbcClient jdbc, MemoReader reader, ApplicationEventPublisher events) {
            this.jdbc = jdbc;
            this.reader = reader;
            this.events = events;
        }

        private long insert(String body) {
            return jdbc.sql("INSERT INTO lab_memos (body) VALUES (:body) RETURNING id")
                    .param("body", body).query(Long.class).single();
        }

        /** 悪い例：トランザクションの途中で @Async を呼ぶ。 */
        @Transactional
        void createBad() throws InterruptedException {
            long id = insert("bad");
            Ch29AsyncLabApplication.say("[bad] INSERT した id=" + id + " / トランザクション中? "
                    + TransactionSynchronizationManager.isActualTransactionActive());
            reader.readLater(id);
            Thread.sleep(500);   // このあとも処理が続く（コミットはメソッドを抜けたとき）
            Ch29AsyncLabApplication.say("[bad] メソッドを抜ける（ここでコミット）");
        }

        /** 良い例：コミットされたあとに動く処理として登録する。 */
        @Transactional
        void createGood() throws InterruptedException {
            long id = insert("good");
            Ch29AsyncLabApplication.say("[good] INSERT した id=" + id);
            events.publishEvent(new MemoCreated(id));
            Thread.sleep(500);
            Ch29AsyncLabApplication.say("[good] メソッドを抜ける（ここでコミット）");
        }
    }

    @Component
    static class MemoReader {

        private final JdbcClient jdbc;

        MemoReader(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Async
        void readLater(long id) {
            report("[bad]", id);
        }

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onCreated(MemoCreated event) {
            report("[good]", event.id());
        }

        private void report(String tag, long id) {
            long found = jdbc.sql("SELECT count(*) FROM lab_memos WHERE id = :id").param("id", id)
                    .query(Long.class).single();
            Ch29AsyncLabApplication.say(tag + " 別スレッドで id=" + id + " を読む → "
                    + (found == 1 ? "見つかった" : "見つからない")
                    + " / トランザクション中? " + TransactionSynchronizationManager.isActualTransactionActive());
        }
    }

    @Bean
    @Order(1)
    ApplicationRunner run(MemoService service, JdbcClient jdbc) {
        return args -> {
            jdbc.sql("CREATE TABLE IF NOT EXISTS lab_memos (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, body text)")
                    .update();
            service.createBad();
            Thread.sleep(500);
            service.createGood();
        };
    }
}
