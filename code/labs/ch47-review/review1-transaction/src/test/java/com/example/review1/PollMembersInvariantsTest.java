package com.example.review1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import com.example.review1.infra.ArchiveWriter;
import com.example.review1.infra.EventRecorder;
import com.example.review1.infra.MemberStore;
import com.example.review1.infra.PollMembers;
import com.example.review1.infra.WebhookClient;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * 「守る約束」をそのままテストにしたもの。bad にも fixed にも、同じテストを流す。
 * bad では赤（失敗）になるのが正しい。fixed では緑になる。
 */
abstract class PollMembersInvariantsTest {

    static final int MAX = 50;

    @Autowired
    PollMembers service;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    DataSource dataSource;

    @MockitoSpyBean
    MemberStore store;
    @MockitoBean
    WebhookClient webhook;
    @MockitoBean
    EventRecorder events;
    @MockitoBean
    ArchiveWriter archive;

    long pollId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE polls, participants, options, poll_events RESTART IDENTITY CASCADE").update();
        pollId = jdbc.sql("INSERT INTO polls (title) VALUES ('ランチ') RETURNING id").query(Long.class).single();
    }

    private int participants() {
        return jdbc.sql("SELECT count(*) FROM participants WHERE poll_id = :p").param("p", pollId)
                .query(Integer.class).single();
    }

    // ---- 約束1: 参加者は50人を超えない（同時に参加されても） ----

    @Test
    void 参加者は同時に参加されても50人を超えない() throws Exception {
        for (long v = 1; v <= MAX - 1; v++) {                         // すでに49人
            jdbc.sql("INSERT INTO participants (poll_id, visitor_id) VALUES (:p, :v)")
                    .param("p", pollId).param("v", v).update();
        }
        // 競合の窓を広げる。数えた直後に50ms止める（本番コードは変えない）。
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            Thread.sleep(50);
            return count;
        }).when(store).countParticipants(anyLong());

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long visitorId = 1000 + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.join(pollId, visitorId);
                        succeeded.incrementAndGet();
                    } catch (IllegalStateException full) {
                        // 満員。想定内の拒否
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }
        System.out.printf("[約束1] 49人 + 同時に%d人 → 成功 %d 件 / 参加者 %d 人%n", threads, succeeded.get(), participants());
        assertThat(participants()).isLessThanOrEqualTo(MAX);
        assertThat(succeeded.get()).isEqualTo(1);
    }

    // ---- 約束2: 外部APIの呼び出し中に、DBの接続とトランザクションを握らない ----

    @Test
    void 外部通知の最中はDB接続を握っていない() {
        AtomicInteger activeConnections = new AtomicInteger(-1);
        AtomicInteger inTransaction = new AtomicInteger(-1);
        doAnswer(invocation -> {
            activeConnections.set(
                    dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive() ? 1 : 0);
            return null;
        }).when(webhook).notifyJoined(anyLong(), anyLong());

        service.join(pollId, 1);

        System.out.printf("[約束2] 通知の最中: 使用中の接続 %d 本 / トランザクション %s%n",
                activeConnections.get(), inTransaction.get() == 1 ? "あり" : "なし");
        assertThat(activeConnections.get()).isZero();
        assertThat(inTransaction.get()).isZero();
    }

    // ---- 約束3: 処理が失敗したなら、一部だけ確定してはいけない（選択肢とイベントは同時に残る） ----

    @Test
    void イベントの記録に失敗したら選択肢の追加も取り消される() {
        doThrow(new IllegalStateException("イベント基盤が応答しません")).when(events).record(anyLong(), anyString());

        Throwable thrown = null;
        try {
            service.addOption(pollId, "カレー");
        } catch (RuntimeException e) {
            thrown = e;
        }
        int options = jdbc.sql("SELECT count(*) FROM options").query(Integer.class).single();
        int eventRows = jdbc.sql("SELECT count(*) FROM poll_events").query(Integer.class).single();
        System.out.printf("[約束3] 呼び出し側の結果: %s / options=%d, events=%d%n",
                thrown == null ? "成功として返った" : "例外", options, eventRows);
        assertThat(thrown).isNotNull();
        assertThat(options).isZero();
    }

    // ---- 約束4: 呼び出し側に失敗を返したなら、DBも変わっていない（検査例外でも） ----

    @Test
    void アーカイブの書き出しに失敗したら締切も取り消される() throws Exception {
        doThrow(new IOException("ディスクがいっぱいです")).when(archive).writeSnapshot(anyLong());

        assertThatThrownBy(() -> service.close(pollId)).isInstanceOf(IOException.class);

        String status = jdbc.sql("SELECT status FROM polls WHERE id = :p").param("p", pollId)
                .query(String.class).single();
        System.out.printf("[約束4] close() は IOException で失敗 → polls.status = %s%n", status);
        assertThat(status).isEqualTo("OPEN");
    }
}
