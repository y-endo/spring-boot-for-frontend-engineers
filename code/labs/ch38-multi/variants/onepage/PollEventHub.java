package com.example.quickvote.event;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.quickvote.common.Db;
import com.example.quickvote.common.QuickVoteProperties;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * このインスタンスにつながっている SSE 購読者の名簿（メモリ内）と、配信の仕組み。
 * <p>
 * 配信の流れ: NOTIFY を受ける（PollEventListener）→ onNotify(pollId) →
 * その投票の購読者それぞれについて「最後に送った seq より後」を DB から読み直して送る。
 * 通知の中身（何が起きたか）は信用せず、いつも DB（poll_events）を正とする。
 * だから通知を取りこぼしても、次の通知や再接続で追いつける。
 * 自分のインスタンスで起きた変更も、他インスタンスで起きた変更も、同じ経路（NOTIFY→DB読み直し）で届く。
 * <p>
 * <b>書き込みは専用のスレッドプールで行う。</b> SseEmitter への書き込みは同期（ブロッキング）で、読み取りを止めた
 * クライアントに送ると、ソケットの送信バッファが埋まった時点で書き込みが止まる。それを通知を受けるスレッド
 * （LISTEN）や @Scheduled のスレッド（ハートビート・期限切れ削除）でやると、1人の遅いクライアントが全員の配信、
 * 定期処理、アプリの停止まで巻き込む。そこで:
 * <ul>
 * <li>書き込みは固定数のスレッドの配信プールで行う。呼び出し側（onNotify・sendHeartbeats）は「配信してほしい」と
 * 頼むだけで、すぐ戻る</li>
 * <li>購読者ごとに「同時に1つだけ」配信し（順序を守る）、頼まれ事は束ねる。遅い購読者が占有するスレッドは最大1つ</li>
 * <li>1回の書き込みが sendTimeout を超えて終わらない購読者は、ハートビートの定期処理が名簿から外す（打ち切り）</li>
 * <li>停止時（closeAll）は待つ時間に上限を置く</li>
 * </ul>
 */
@Component
public class PollEventHub implements DisposableBean {

    /** 送信先の抽象。本番は SseEmitter、テストでは単なるリスト。 */
    public interface EventSink {
        void send(PollEvent event) throws IOException;

        void heartbeat() throws IOException;

        /** ストリームを正常に終了する（サーバー停止時や打ち切りに使う）。 */
        void complete();
    }

    /**
     * 1接続ぶんの状態。lastSentSeq などの「配信側の状態」は、配信（deliver）の中でしか触らない。
     * 配信は購読者ごとに同時に1つしか走らないので（request / drain）、ロックは要らない。
     */
    public static final class Subscription {
        private final long pollId;
        private final EventSink sink;
        private long lastSentSeq;
        /** 非 null なら、最初に RESYNC を送り、そこから続きを送る（クライアントがサーバーより先に進んでいた場合）。 */
        private @Nullable Long resyncSeq;
        private volatile boolean heartbeatDue;
        /** 配信してほしい、という頼まれ事の数。0 → 1 になったときだけ配信プールに仕事を出す。 */
        private final AtomicInteger requests = new AtomicInteger();
        /** いま書き込み中ならその開始時刻。書き込みが終わったら null。ハートビートの定期処理が見張る。 */
        private volatile @Nullable Instant sendingSince;
        private volatile boolean closed;
        /** 詰まっているとして打ち切られた。書き込みが戻ってきたら、ストリームを終わらせる。 */
        private volatile boolean abandoned;

        private Subscription(long pollId, EventSink sink, long lastSentSeq) {
            this.pollId = pollId;
            this.sink = sink;
            this.lastSentSeq = lastSentSeq;
        }

        public long pollId() {
            return pollId;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PollEventHub.class);

    /** 停止時に、ストリームを閉じる処理を待つ最大時間。詰まった接続のせいで、アプリの停止を止めない。 */
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(3);

    private final PollEventService events;
    private final Clock clock;
    private final Duration sendTimeout;
    private final ExecutorService executor;
    private final Map<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public PollEventHub(PollEventService events, Clock clock, QuickVoteProperties properties) {
        this.events = events;
        this.clock = clock;
        this.sendTimeout = properties.sse().sendTimeout();
        this.executor = Executors.newFixedThreadPool(properties.sse().deliveryThreads(), deliveryThreadFactory());
    }

    private static ThreadFactory deliveryThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> Thread.ofPlatform().name("sse-delivery-" + counter.incrementAndGet()).daemon()
                .unstarted(runnable);
    }

    /**
     * 購読を始める。lastEventId があれば、それより後のイベントを先に再送する。なければ現在の最新から。
     * <p>
     * 順序が大事: ①名簿に登録 → ②再送。先に名簿へ入れておけば、再送中に起きた変更の通知も
     * 取りこぼさない（通知で配信が頼まれ、lastSentSeq 以降を送るため、重複も欠落も起きない）。
     * 再送の書き込みはこのスレッド（HTTPリクエストのスレッド）ではなく、配信プールで行う。
     */
    public Subscription subscribe(long pollId, @Nullable Long lastEventId, EventSink sink) {
        long latest = events.lastSeq(pollId);
        Subscription subscription = new Subscription(pollId, sink, lastEventId == null ? latest : lastEventId);
        if (lastEventId != null && lastEventId > latest) {
            // クライアントの方が先に進んでいる（DBが作り直された等）。履歴で補完できないので全量取得を促す。
            subscription.resyncSeq = latest;
        }
        // 「Set の取得・作成・追加」を compute の中でひとまとめにする。computeIfAbsent(...).add(x) と分けると、
        // 取得した Set が、追加する前に最後の購読解除（close）で空になって名簿から外され、新しい購読が名簿に載らなくなる。
        // close() も同じキーの compute 系なので、同じキーに対する操作は直列になる。
        subscriptions.compute(pollId, (id, set) -> {
            Set<Subscription> target = set != null ? set : ConcurrentHashMap.newKeySet();
            target.add(subscription);
            return target;
        });
        request(subscription);
        return subscription;
    }

    /** ロックなしで外せる（書き込み中の配信を待たない）。 */
    public void unsubscribe(Subscription subscription) {
        close(subscription);
    }

    private void close(Subscription subscription) {
        subscription.closed = true;
        subscriptions.computeIfPresent(subscription.pollId, (id, set) -> {
            set.remove(subscription);
            return set.isEmpty() ? null : set;
        });
    }

    /** NOTIFY を受けたときに呼ばれる。その投票の購読者へ、未送信のイベントを届けるよう頼む（すぐ戻る）。 */
    public void onNotify(long pollId) {
        Set<Subscription> targets = subscriptions.get(pollId);
        if (targets != null) {
            targets.forEach(this::request);
        }
    }

    /** リスナーの再接続直後などに、全購読者を DB の最新に追いつかせるよう頼む（すぐ戻る）。 */
    public void deliverAll() {
        subscriptions.values().forEach(set -> set.forEach(this::request));
    }

    /**
     * 「配信してほしい」と頼む。購読者ごとに、頼まれ事の数を数える。
     * 0 → 1 になったときだけ配信プールに仕事を出す（すでに配信中・待機中なら、その仕事が後で拾う）。
     * だから同じ購読者への配信は同時に1つ、頼まれ事は取りこぼさず、束ねられる。
     */
    private void request(Subscription subscription) {
        if (subscription.closed) {
            return;
        }
        if (subscription.requests.getAndIncrement() == 0) {
            try {
                executor.execute(() -> drain(subscription));
            } catch (RejectedExecutionException e) {
                // 停止処理で配信プールが閉じられた後。もう配信しない。
                subscription.requests.set(0);
            }
        }
    }

    private void drain(Subscription subscription) {
        int handled;
        do {
            handled = subscription.requests.get();
            deliver(subscription);
            // 配信している間に新しい頼まれ事があれば（0 に戻らなければ）、もう一度 DB を読み直す。
        } while (!subscription.closed && subscription.requests.addAndGet(-handled) > 0);
        if (subscription.abandoned) {
            // 詰まっていて打ち切られた購読者。書き込みが戻ってきたので、ストリームを終わらせる
            // （放っておくと、クライアントは「つながっているのに何も届かない」状態になる。終われば EventSource が再接続する）。
            subscription.sink.complete();
        }
    }

    /** 未送信のイベントを、最新に追いつくまで送る。1回の DB 読み出しは PAGE_SIZE 件までなので、ページごとに繰り返す。 */
    private void deliver(Subscription subscription) {
        try {
            if (subscription.resyncSeq != null) {
                long resyncSeq = subscription.resyncSeq;
                send(subscription, new PollEvent(resyncSeq, "RESYNC", "{}", Db.now(clock)));
                subscription.lastSentSeq = resyncSeq;
                subscription.resyncSeq = null;
            }
            if (subscription.heartbeatDue) {
                subscription.heartbeatDue = false;
                sendHeartbeat(subscription);
            }
            List<PollEvent> page;
            do {
                page = events.findAfter(subscription.pollId, subscription.lastSentSeq);
                for (PollEvent event : page) {
                    if (subscription.closed) {
                        return;
                    }
                    send(subscription, event);
                    subscription.lastSentSeq = event.seq();
                }
                // 満杯のページが返ったら、まだ続きがある。次の通知を待たずに読み進める。
            } while (false); // [lab] 指摘5の修正前: 最初の1ページ(500件)で止まる版
        } catch (IOException | IllegalStateException e) {
            // クライアントが切断済み。名簿から外す（ログにはメッセージを出さない）。
            log.debug("SSE 購読者への送信に失敗したため購読を解除します: {}", e.getClass().getSimpleName());
            close(subscription);
        }
    }

    // 書き込みの前後で sendingSince を記録する（ハートビートの定期処理が「詰まっていないか」を見るため）。
    // 書き込み中だけを測るので、DB の読み出しが遅いだけでは、詰まっているとは見なされない。
    private void send(Subscription subscription, PollEvent event) throws IOException {
        subscription.sendingSince = clock.instant();
        try {
            subscription.sink.send(event);
        } finally {
            subscription.sendingSince = null;
        }
    }

    private void sendHeartbeat(Subscription subscription) throws IOException {
        subscription.sendingSince = clock.instant();
        try {
            subscription.sink.heartbeat();
        } finally {
            subscription.sendingSince = null;
        }
    }

    /**
     * 無通信でも接続が切られないよう、コメント行を定期的に送るよう頼む。
     * あわせて、書き込みが sendTimeout を超えて終わらない（詰まっている）購読者を名簿から外す。
     * このメソッドは書き込みをしないので、詰まった接続があっても止まらない
     * （@Scheduled のスレッドを、ほかの定期処理から奪わない）。
     */
    @Scheduled(fixedDelayString = "${quickvote.sse.heartbeat-interval:15s}")
    public void sendHeartbeats() {
        Instant now = clock.instant();
        subscriptions.values().forEach(set -> set.forEach(subscription -> {
            Instant since = subscription.sendingSince;
            if (since != null && Duration.between(since, now).compareTo(sendTimeout) > 0) {
                log.warn("SSE の書き込みが詰まっているため購読を打ち切ります: pollId={}", subscription.pollId);
                subscription.abandoned = true;
                close(subscription);
                return;
            }
            subscription.heartbeatDue = true;
            request(subscription);
        }));
    }

    /**
     * サーバー停止時に全接続を終わらせる。SSE の接続は「終わらない（長時間続く）リクエスト」なので、
     * 閉じないと graceful shutdown が処理中のリクエストの完了を待ち続け、タイムアウト(既定30秒)まで止まらない。
     * クライアント（EventSource）は自動で再接続するので、再起動後の別インスタンスにつながり直す。
     * <p>
     * SseEmitter#complete は書き込みと同じロックを取るので、詰まった接続の complete は戻ってこない。
     * 接続ごとに別のスレッド（仮想スレッド）で実行し、待つ時間に上限（CLOSE_WAIT）を置く。
     * 配信プールは使わない（詰まった接続がプールのスレッドを塞いでいると、ほかの接続の complete も実行できなくなる）。
     * 上限を過ぎたら、終わっていない処理は取り消して先へ進む。
     */
    public void closeAll() {
        List<Subscription> all = new ArrayList<>();
        subscriptions.values().forEach(all::addAll);
        subscriptions.clear();
        List<Callable<Void>> completions = new ArrayList<>();
        for (Subscription subscription : all) {
            subscription.closed = true;
            completions.add(() -> {
                subscription.sink.complete();
                return null;
            });
        }
        ExecutorService closer = Executors.newVirtualThreadPerTaskExecutor();
        try {
            closer.invokeAll(completions, CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closer.shutdownNow(); // close() は全タスクの終了を待つので使わない（詰まったタスクを待たない）
        }
    }

    /** アプリケーションコンテキストの終了時に、配信プールを止める。 */
    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    /** 監視・テスト用。 */
    public int subscriberCount() {
        return subscriptions.values().stream().mapToInt(Set::size).sum();
    }
}
