package com.example.quickvote.event;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.support.MutableClock;
import com.example.quickvote.support.TestProperties;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 購読者の名簿と配信の仕組み（PollEventHub）の単体テスト。DB も Spring も使わない。
 * 履歴（PollEventService）は偽物で、メモリ上のリストを返す。送信先は、わざと詰まらせられる偽物。
 */
class PollEventHubTest {

    private static final long POLL = 1L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final List<PollEvent> stored = new CopyOnWriteArrayList<>();
    private PollEventHub hub;

    @BeforeEach
    void setUp() {
        hub = create(2);
    }

    /** 配信スレッドの数を指定して hub を作る。履歴は stored（メモリ上のリスト）から、1ページ PAGE_SIZE 件ずつ返す。 */
    private PollEventHub create(int deliveryThreads) {
        PollEventService events = mock(PollEventService.class);
        when(events.lastSeq(anyLong())).thenAnswer(invocation -> (long) stored.size());
        when(events.findAfter(anyLong(), anyLong())).thenAnswer(invocation -> {
            long after = invocation.getArgument(1);
            return stored.stream().filter(e -> e.seq() > after).limit(PollEventService.PAGE_SIZE).toList();
        });
        QuickVoteProperties props = TestProperties.withSse(new QuickVoteProperties.Sse(Duration.ofSeconds(15),
                Duration.ofMinutes(30), Duration.ofSeconds(10), deliveryThreads));
        return new PollEventHub(events, clock, props);
    }

    @AfterEach
    void tearDown() {
        hub.destroy();
    }

    private void append(int count) {
        for (int i = 0; i < count; i++) {
            long seq = stored.size() + 1;
            stored.add(new PollEvent(seq, "VOTE_CAST", "{}", clock.instant()));
        }
    }

    /** 受け取ったイベントを溜める送信先。 */
    private static class QueueSink implements PollEventHub.EventSink {
        final BlockingQueue<PollEvent> events = new LinkedBlockingQueue<>();
        final AtomicInteger heartbeats = new AtomicInteger();
        volatile boolean completed;

        @Override
        public void send(PollEvent event) throws IOException {
            events.add(event);
        }

        @Override
        public void heartbeat() throws IOException {
            heartbeats.incrementAndGet();
        }

        @Override
        public void complete() {
            completed = true;
        }

        PollEvent next() throws InterruptedException {
            PollEvent event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).as("10秒以内にイベントが届くこと").isNotNull();
            return event;
        }
    }

    /** 読み取りを止めたクライアントの模擬。send は release されるまで戻らない。complete も、書き込みと同じロックで待たされる。 */
    private static class StalledSink extends QueueSink {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void send(PollEvent event) throws IOException {
            entered.countDown();
            await();
            super.send(event);
        }

        @Override
        public void complete() {
            try {
                await(); // SseEmitter#complete は、書き込みと同じロックを取るので、詰まった書き込みの後ろで待たされる
            } catch (IOException e) {
                return;
            }
            super.complete();
        }

        private void await() throws IOException {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("中断された", e);
            }
        }
    }

    @Test
    void 遅い購読者が詰まっても_ほかの購読者への配信_通知の受付_ハートビートは止まらない() throws Exception {
        append(1);
        StalledSink slow = new StalledSink();
        QueueSink fast = new QueueSink();
        hub.subscribe(POLL, 0L, slow);
        assertThat(slow.entered.await(10, TimeUnit.SECONDS)).as("遅い購読者への書き込みが始まって詰まった").isTrue();
        hub.subscribe(POLL, 0L, fast);

        // 遅い購読者が書き込みで詰まっている間も、ほかの購読者は受け取れる
        assertThat(fast.next().seq()).isEqualTo(1);

        // 通知を受けるスレッド（LISTEN）と @Scheduled のスレッドが呼ぶメソッドは、詰まらずにすぐ戻る
        append(1);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> hub.onNotify(POLL));
        assertThat(fast.next().seq()).isEqualTo(2);
        assertTimeoutPreemptively(Duration.ofSeconds(2), hub::sendHeartbeats);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> fast.heartbeats.get() >= 1);

        slow.release.countDown();
        assertThat(slow.next().seq()).isEqualTo(1);
        assertThat(slow.next().seq()).isEqualTo(2); // 詰まりが解けたら、続きを順番どおりに受け取る
    }

    @Test
    void 書き込みがsendTimeoutを超えて詰まった購読者は打ち切られ_書き込みが戻ったらストリームを閉じる() throws Exception {
        append(1);
        StalledSink slow = new StalledSink();
        QueueSink healthy = new QueueSink();
        hub.subscribe(POLL, 0L, slow);
        hub.subscribe(POLL, 0L, healthy);
        assertThat(slow.entered.await(10, TimeUnit.SECONDS)).isTrue();
        healthy.next();
        assertThat(hub.subscriberCount()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(9));
        hub.sendHeartbeats();
        assertThat(hub.subscriberCount()).as("sendTimeout(10秒)以内は待つ").isEqualTo(2);

        clock.advance(Duration.ofSeconds(2)); // 詰まってから11秒
        hub.sendHeartbeats();
        assertThat(hub.subscriberCount()).as("詰まった購読者だけが名簿から外れる").isEqualTo(1);

        slow.release.countDown(); // クライアントが読み取りを再開した（または Tomcat の書き込みが時間切れになった）
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> slow.completed);
        append(1);
        hub.onNotify(POLL);
        assertThat(healthy.next().seq()).isEqualTo(2); // 健全な購読者は、そのまま受け取り続ける
        assertThat(slow.events).extracting(PollEvent::seq).containsExactly(1L); // 打ち切られた購読者には、もう送らない
    }

    @Test
    void 停止処理は_詰まった接続があっても待ち時間の上限で戻る() throws Exception {
        append(1);
        StalledSink slow1 = new StalledSink();
        StalledSink slow2 = new StalledSink();
        QueueSink healthy = new QueueSink();
        hub.subscribe(POLL, 0L, slow1);
        hub.subscribe(POLL, 0L, slow2); // 配信スレッド(2本)が両方とも詰まる
        assertThat(slow1.entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(slow2.entered.await(10, TimeUnit.SECONDS)).isTrue();
        hub.subscribe(POLL, 0L, healthy);

        // 詰まった接続の complete は戻ってこない。それでも closeAll は上限（3秒）で戻り、ほかの接続は閉じられる。
        assertTimeoutPreemptively(Duration.ofSeconds(8), hub::closeAll);

        assertThat(healthy.completed).isTrue();
        assertThat(hub.subscriberCount()).isZero();
        slow1.release.countDown();
        slow2.release.countDown();
    }

    @Test
    void 未送信のイベントが複数ページあっても_次の通知を待たずに最後まで届く() throws Exception {
        append(PollEventService.PAGE_SIZE * 2 + 37); // 1037件
        QueueSink sink = new QueueSink();

        hub.subscribe(POLL, 0L, sink);

        for (int seq = 1; seq <= stored.size(); seq++) {
            assertThat(sink.next().seq()).isEqualTo(seq);
        }
        assertThat(sink.events.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 通知が重なっても_購読者ごとに順番どおり_重複も欠落もなく届く() throws Exception {
        QueueSink sink = new QueueSink();
        hub.subscribe(POLL, null, sink);
        int total = 300;
        ExecutorService notifier = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> writer = notifier.submit(() -> {
                start.await();
                for (int i = 0; i < total; i++) {
                    append(1);
                    hub.onNotify(POLL);
                }
                return null;
            });
            // 通知だけを大量に重ねる（別のインスタンスの NOTIFY や再接続直後の deliverAll を模す）
            Future<?> spam1 = notifier.submit(() -> {
                start.await();
                for (int i = 0; i < 2000; i++) {
                    hub.onNotify(POLL);
                }
                return null;
            });
            Future<?> spam2 = notifier.submit(() -> {
                start.await();
                for (int i = 0; i < 2000; i++) {
                    hub.deliverAll();
                }
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            spam1.get(30, TimeUnit.SECONDS);
            spam2.get(30, TimeUnit.SECONDS);
        } finally {
            notifier.shutdownNow();
        }
        hub.onNotify(POLL);

        for (int seq = 1; seq <= total; seq++) {
            assertThat(sink.next().seq()).isEqualTo(seq);
        }
        assertThat(sink.events.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 購読の登録と最後の購読解除が競合しても_新しい購読が名簿から消えない() throws Exception {
        // 以前の実装: computeIfAbsent(...).add(購読) は原子的ではなかった。
        // 登録側が既存の Set を取得した直後に、解除側が最後の要素を消して Set ごと名簿から外すと、
        // 登録側は名簿に載っていない Set へ追加してしまい、以後その購読には通知もハートビートも届かない。
        // タイミング依存の競合なので、2つの操作を同時に始める形を何度も繰り返す。
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20_000; i++) {
                PollEventHub.Subscription first = hub.subscribe(POLL, null, new QueueSink());
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<PollEventHub.Subscription> second = pool.submit(() -> {
                    barrier.await();
                    return hub.subscribe(POLL, null, new QueueSink());
                });
                barrier.await();
                hub.unsubscribe(first);
                PollEventHub.Subscription registered = second.get(10, TimeUnit.SECONDS);

                assertThat(hub.subscriberCount()).as("%d 回目: 登録した購読が名簿にある", i).isEqualTo(1);
                hub.unsubscribe(registered);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void クライアントの方が先に進んでいたらRESYNCを送り_そこから続きを送る() throws Exception {
        append(3);
        QueueSink sink = new QueueSink();

        hub.subscribe(POLL, 99L, sink);
        PollEvent resync = sink.next();
        assertThat(resync.type()).isEqualTo("RESYNC");
        assertThat(resync.seq()).isEqualTo(3);

        append(1);
        hub.onNotify(POLL);
        assertThat(sink.next().seq()).isEqualTo(4);
    }
}
