package com.example.quickvote;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.quickvote.event.PollEvent;
import com.example.quickvote.event.PollEventHub;
import com.example.quickvote.event.PollEventService;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.vote.VoteService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * イベント履歴（poll_events）と配信（PollEventHub）。
 * SSE の HTTP 部分は RealHttpIT で見る。ここは「保存」「再送」「NOTIFY 経由の配信」をサービス層で確かめる。
 */
class PollEventIT extends AbstractIntegrationTest {

    @Autowired
    PollEventService eventService;
    @Autowired
    PollEventHub hub;
    @Autowired
    VoteService voteService;
    @Autowired
    TransactionTemplate transaction;

    /** テスト用の送信先。受け取ったイベントをキューに溜める。 */
    static class QueueSink implements PollEventHub.EventSink {
        final BlockingQueue<PollEvent> events = new LinkedBlockingQueue<>();
        final AtomicInteger heartbeats = new AtomicInteger();
        volatile boolean broken;

        @Override
        public void send(PollEvent event) throws IOException {
            if (broken) {
                throw new IOException("切断済み");
            }
            events.add(event);
        }

        @Override
        public void heartbeat() throws IOException {
            if (broken) {
                throw new IOException("切断済み");
            }
            heartbeats.incrementAndGet();
        }

        @Override
        public void complete() {
            completed = true;
        }

        volatile boolean completed;

        PollEvent next() throws InterruptedException {
            PollEvent event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).as("10秒以内にイベントが届くこと").isNotNull();
            return event;
        }
    }

    private final List<PollEventHub.Subscription> subscriptions = new java.util.ArrayList<>();

    private PollEventHub.Subscription subscribe(long pollId, Long lastEventId, PollEventHub.EventSink sink) {
        PollEventHub.Subscription subscription = hub.subscribe(pollId, lastEventId, sink);
        subscriptions.add(subscription);
        return subscription;
    }

    /** hub は全テスト共通の Bean。テストが終わったら購読を外して、次のテストに持ち越さない。 */
    @AfterEach
    void unsubscribeAll() {
        subscriptions.forEach(hub::unsubscribe);
    }

    private long internalId(PollDetail poll) {
        return jdbc.sql("SELECT id FROM polls WHERE public_id = :id").param("id", poll.id()).query(Long.class).single();
    }

    /** 参加(1) → 選択肢追加(2) → 投票(3) → 投票変更(4) の4イベントを作る。 */
    private PollDetail scenario(TestVisitor host, TestVisitor member) {
        PollDetail poll = createPoll(host, true, "A", "B");
        join(member, poll);
        pollService.addOption(member.id(), poll.id(), "k", "C");
        voteService.vote(member.id(), poll.id(), poll.options().get(0).id());
        voteService.vote(member.id(), poll.id(), poll.options().get(1).id());
        return poll;
    }

    @Test
    void 業務更新と同じトランザクションで_連番つきのイベントが保存される() {
        TestVisitor host = newVisitor();
        TestVisitor member = newVisitor();
        PollDetail poll = scenario(host, member);

        List<PollEvent> events = eventService.findAfter(internalId(poll), 0);

        assertThat(events).extracting(PollEvent::seq).containsExactly(1L, 2L, 3L, 4L);
        assertThat(events).extracting(PollEvent::type)
                .containsExactly("PARTICIPANT_JOINED", "OPTION_ADDED", "VOTE_CAST", "VOTE_CAST");
        assertThat(events.get(0).payload()).contains("\"participantCount\"");
    }

    @Test
    void 同じ選択肢への再投票は票が変わらないのでイベントを出さない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long optionA = poll.options().get(0).id();

        voteService.vote(host.id(), poll.id(), optionA);
        voteService.vote(host.id(), poll.id(), optionA);

        assertThat(countRows("poll_events")).isEqualTo(1);
    }

    @Test
    void 失敗した更新ではイベントも連番も残らない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        clock.set(poll.closesAt());

        Throwable thrown = catchThrowable(() -> voteService.vote(host.id(), poll.id(), poll.options().get(0).id()));

        assertThat(thrown).isNotNull();
        assertThat(countRows("poll_events")).isZero();
        assertThat(jdbc.sql("SELECT last_event_seq FROM polls").query(Long.class).single()).isZero();
    }

    @Test
    void ロールバックされたトランザクションのイベントは残らない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long pollId = internalId(poll);

        catchThrowable(() -> transaction.executeWithoutResult(status -> {
            eventService.append(pollId, "VOTE_CAST", java.util.Map.of("optionId", 1));
            throw new IllegalStateException("途中で失敗");
        }));

        assertThat(countRows("poll_events")).isZero();
        assertThat(eventService.lastSeq(pollId)).isZero(); // 採番(UPDATE)も一緒に取り消される
    }

    @Test
    void Last_Event_ID以降のイベントだけが履歴として取り出せる() {
        TestVisitor host = newVisitor();
        PollDetail poll = scenario(host, newVisitor());

        assertThat(eventService.findAfter(internalId(poll), 2)).extracting(PollEvent::seq).containsExactly(3L, 4L);
        assertThat(eventService.findAfter(internalId(poll), 4)).isEmpty();
    }

    @Test
    void 購読時にLast_Event_IDがあれば履歴を再送してから_新着をライブで受け取る() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = scenario(host, newVisitor());
        QueueSink sink = new QueueSink();

        subscribe(internalId(poll), 2L, sink);

        // 再送: 3, 4
        assertThat(sink.next().seq()).isEqualTo(3);
        assertThat(sink.next().seq()).isEqualTo(4);
        // ライブ: この後の投票は NOTIFY → DB読み直し → 配信 の経路で届く（重複も欠落もなく seq 5）
        pollService.close(host.id(), poll.id());
        PollEvent live = sink.next();
        assertThat(live.seq()).isEqualTo(5);
        assertThat(live.type()).isEqualTo("POLL_CLOSED");
        assertThat(sink.events).isEmpty();
    }

    @Test
    void Last_Event_IDなしで購読すると_過去は送らず新着だけ受け取る() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = scenario(host, newVisitor());
        QueueSink sink = new QueueSink();

        subscribe(internalId(poll), null, sink);
        assertThat(sink.events.poll(500, TimeUnit.MILLISECONDS)).isNull();

        pollService.close(host.id(), poll.id());
        assertThat(sink.next().seq()).isEqualTo(5);
    }

    @Test
    void クライアントの方が先に進んでいたら_RESYNC_で全量の取り直しを促す() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = scenario(host, newVisitor());
        QueueSink sink = new QueueSink();

        subscribe(internalId(poll), 999L, sink);

        PollEvent resync = sink.next();
        assertThat(resync.type()).isEqualTo("RESYNC");
        assertThat(resync.seq()).isEqualTo(4); // 以後はここから続きを受け取る
    }

    @Test
    void 別インスタンスが書いたイベントもNOTIFY経由で届く() throws Exception {
        // 2台構成の再現: 「別のインスタンス」はこの hub を一切通さず、DB に直接イベントを書いて NOTIFY するだけ。
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long pollId = internalId(poll);
        QueueSink sink = new QueueSink();
        subscribe(pollId, null, sink);

        transaction.executeWithoutResult(status -> {
            jdbc.sql("UPDATE polls SET last_event_seq = 1 WHERE id = :id").param("id", pollId).update();
            jdbc.sql("INSERT INTO poll_events (poll_id, seq, type, payload, created_at) "
                    + "VALUES (:id, 1, 'VOTE_CAST', '{\"from\":\"other-instance\"}', :now)")
                    .param("id", pollId).param("now", Instant.now().atOffset(java.time.ZoneOffset.UTC)).update();
            jdbc.sql("SELECT pg_notify('poll_events', :id)").param("id", String.valueOf(pollId)).query().singleValue();
        });

        PollEvent received = sink.next();
        assertThat(received.seq()).isEqualTo(1);
        assertThat(received.payload()).contains("other-instance");
    }

    @Test
    void LISTEN接続が切れても自動で再接続し_切断中に起きた変更も追いついて届く() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        QueueSink sink = new QueueSink();
        subscribe(internalId(poll), null, sink);

        // DB 側から LISTEN 中の接続を強制的に切る（ネットワーク断・DB再起動の再現）
        int terminated = jdbc.sql("""
                SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity
                WHERE pid <> pg_backend_pid() AND query = 'LISTEN poll_events'
                """).query(Integer.class).single();
        assertThat(terminated).isGreaterThanOrEqualTo(1);

        // 切断中に起きた変更の NOTIFY は失われる。再接続後の deliverAll（DB読み直し）で追いつく。
        pollService.close(host.id(), poll.id());

        assertThat(sink.next().type()).isEqualTo("POLL_CLOSED");
    }

    @Test
    void コミットされなかったNOTIFYは配送されない() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long pollId = internalId(poll);
        QueueSink sink = new QueueSink();
        subscribe(pollId, null, sink);

        catchThrowable(() -> transaction.executeWithoutResult(status -> {
            eventService.append(pollId, "VOTE_CAST", java.util.Map.of());
            throw new IllegalStateException("ロールバックさせる");
        }));

        assertThat(sink.events.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void 切断された購読者は配信時に名簿から外される() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        QueueSink sink = new QueueSink();
        subscribe(internalId(poll), null, sink);
        int before = hub.subscriberCount();

        sink.broken = true;
        pollService.close(host.id(), poll.id()); // 配信を試みて失敗する

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(hub.subscriberCount()).isEqualTo(before - 1));
    }

    @Test
    void サーバー停止時は全購読者のストリームを終了する() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        QueueSink sink = new QueueSink();
        hub.subscribe(internalId(poll), null, sink); // テスト後の掃除対象にはしない（closeAll で外れる）

        hub.closeAll();

        assertThat(sink.completed).isTrue();
        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void ハートビートは全購読者に送られ_送れない購読者は外される() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        QueueSink alive = new QueueSink();
        QueueSink dead = new QueueSink();
        subscribe(internalId(poll), null, alive);
        subscribe(internalId(poll), null, dead);
        dead.broken = true;
        int before = hub.subscriberCount();

        hub.sendHeartbeats(); // 送信は配信プールで行われる（このメソッドは頼むだけで、すぐ戻る）

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(alive.heartbeats.get()).isGreaterThanOrEqualTo(1);
            assertThat(hub.subscriberCount()).isEqualTo(before - 1);
        });
    }

    @Test
    void 未送信のイベントが1ページ_500件_を超えていても_次の通知を待たずに最後まで再送される() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long pollId = internalId(poll);
        // 切断中に501件のイベントが確定した状況（1ページは500件。以前は500件で止まり、501件目は次の通知まで届かなかった）
        int total = 501;
        jdbc.sql("UPDATE polls SET last_event_seq = :n WHERE id = :id").param("n", total).param("id", pollId).update();
        jdbc.sql("""
                INSERT INTO poll_events (poll_id, seq, type, payload, created_at)
                SELECT :id, s, 'VOTE_CAST', '{}', now() FROM generate_series(1, :n) AS s
                """).param("id", pollId).param("n", total).update();
        QueueSink sink = new QueueSink();

        subscribe(pollId, 0L, sink); // Last-Event-ID: 0 で再接続。このあと、新しい更新は一切起こさない

        for (int seq = 1; seq <= total; seq++) {
            assertThat(sink.next().seq()).isEqualTo(seq);
        }
        assertThat(sink.events.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 複数ページ分の遅れ_1200件_でも欠落も重複もなく追いつく() throws Exception {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long pollId = internalId(poll);
        int total = 1200;
        jdbc.sql("UPDATE polls SET last_event_seq = :n WHERE id = :id").param("n", total).param("id", pollId).update();
        jdbc.sql("""
                INSERT INTO poll_events (poll_id, seq, type, payload, created_at)
                SELECT :id, s, 'VOTE_CAST', '{}', now() FROM generate_series(1, :n) AS s
                """).param("id", pollId).param("n", total).update();
        QueueSink sink = new QueueSink();

        subscribe(pollId, 100L, sink);

        for (int seq = 101; seq <= total; seq++) {
            assertThat(sink.next().seq()).isEqualTo(seq);
        }
        assertThat(sink.events.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }
}
