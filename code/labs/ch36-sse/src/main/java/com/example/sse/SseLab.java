package com.example.sse;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 第36章の実験用エンドポイント集。1つ1つが「観察したいこと」に対応している。 */
@RestController
public class SseLab {

    private static final Logger log = LoggerFactory.getLogger(SseLab.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // ---- 実験1: 最初の書き込みまでヘッダーが出ない ----

    /** 何も書かずに emitter を返す。3秒後に最初のイベントを送る。 */
    @GetMapping(path = "/sse/silent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter silent() {
        SseEmitter emitter = new SseEmitter(30_000L);
        scheduler.schedule(
                () -> send(emitter, SseEmitter.event().id("1").name("hello").data("3秒後の最初のイベント")),
                3, TimeUnit.SECONDS);
        return emitter;
    }

    /** 接続直後にコメント行と retry を書く。ヘッダーもすぐ出る。以後1秒ごとにイベント。Last-Event-ID はログに出す。 */
    @GetMapping(path = "/sse/hello", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter hello(@RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        log.info("/sse/hello 接続 Last-Event-ID={}", lastEventId);
        SseEmitter emitter = new SseEmitter(30_000L);
        send(emitter, SseEmitter.event().comment("connected").reconnectTime(3000));
        AtomicLong id = new AtomicLong(lastEventId == null ? 0 : Long.parseLong(lastEventId));
        var task = scheduler.scheduleAtFixedRate(
                () -> send(emitter, SseEmitter.event().id(String.valueOf(id.incrementAndGet())).name("tick")
                        .data("{\"n\": " + id.get() + "}")),
                1, 1, TimeUnit.SECONDS);
        emitter.onCompletion(() -> task.cancel(false));
        emitter.onError(e -> task.cancel(false));
        return emitter;
    }

    // ---- 実験2: タイムアウトと切断の検出 ----

    /** timeout ミリ秒で終わる。2秒ごとにハートビート。コールバックが呼ばれる様子をログに出す。 */
    @GetMapping(path = "/sse/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter watch(@RequestParam(defaultValue = "8000") long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        log.info("/sse/watch 接続 timeout={}ms thread={}", timeout, Thread.currentThread());
        send(emitter, SseEmitter.event().comment("connected"));
        var task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
                log.info("  heartbeat 送信OK");
            } catch (Exception e) {
                log.info("  heartbeat 送信失敗: {}", e.getClass().getSimpleName());
            }
        }, 2, 2, TimeUnit.SECONDS);
        emitter.onTimeout(() -> {
            log.info("  onTimeout");
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            log.info("  onCompletion");
            task.cancel(false);
        });
        emitter.onError(e -> {
            log.info("  onError {}", e.getClass().getSimpleName());
            task.cancel(false);
        });
        return emitter;
    }

    // ---- 実験3: 仮想スレッド ON/OFF ----

    private final List<SseEmitter> held = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ticking = new AtomicBoolean();

    /** SseEmitter 方式: リクエストスレッドは emitter を返してすぐ手放す。1本の共有スケジューラが全接続に書く。 */
    @GetMapping(path = "/sse/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter emitter() {
        SseEmitter emitter = new SseEmitter(0L);
        send(emitter, SseEmitter.event().comment("connected"));
        held.add(emitter);
        emitter.onCompletion(() -> held.remove(emitter));
        emitter.onError(e -> held.remove(emitter));
        if (ticking.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                    () -> held.forEach(e -> send(e, SseEmitter.event().comment("tick"))), 1, 1, TimeUnit.SECONDS);
        }
        return emitter;
    }

    /** 古典的な方式: リクエストを処理するスレッドの中で、接続が続く限り書き続ける。1接続＝1スレッドを握る。 */
    @GetMapping(path = "/sse/blocking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void blocking(HttpServletResponse response) throws IOException, InterruptedException {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        PrintWriter out = response.getWriter();
        out.write(":connected\n\n");
        out.flush();
        while (!out.checkError()) {
            Thread.sleep(1000);
            out.write(":tick\n\n");
            out.flush();
        }
    }

    /** スレッドの状況。Tomcat の処理スレッドが何本あるか、いまのスレッドは仮想か。 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var infos = ManagementFactory.getThreadMXBean().dumpAllThreads(false, false);
        long tomcat = Arrays.stream(infos).filter(t -> t.getThreadName().startsWith("http-nio")).count();
        return Map.of(
                "requestThread", Thread.currentThread().toString(),
                "isVirtual", Thread.currentThread().isVirtual(),
                "platformThreads", ManagementFactory.getThreadMXBean().getThreadCount(),
                "tomcatThreads", tomcat,
                "heldEmitters", held.size());
    }

    // ---- 実験4: 遅いクライアントが配信全体を止める（素朴な全員配信） ----

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    @GetMapping(path = "/sse/broadcast", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        send(emitter, SseEmitter.event().comment("connected"));
        subscribers.add(emitter);
        log.info("購読者が増えました: {}人", subscribers.size());
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onError(e -> subscribers.remove(emitter));
        return emitter;
    }

    /** 全購読者へ、同じスレッドで順番に同期して書く。kb キロバイトのイベントを everyMs ミリ秒ごとに。 */
    @GetMapping("/sse/broadcast/start")
    public String start(@RequestParam(defaultValue = "64") int kb, @RequestParam(defaultValue = "200") long everyMs) {
        String payload = "x".repeat(kb * 1024);
        AtomicLong n = new AtomicLong();
        Thread.ofPlatform().name("naive-publisher").daemon().start(() -> {
            while (true) {
                long id = n.incrementAndGet();
                long t0 = System.nanoTime();
                for (SseEmitter e : subscribers) {
                    try {
                        e.send(SseEmitter.event().id(String.valueOf(id)).data(payload));
                    } catch (Exception ex) {
                        subscribers.remove(e);
                    }
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                log.info("配信 #{} に {} ms（購読者 {}人）", id, ms, subscribers.size());
                try {
                    Thread.sleep(everyMs);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        });
        return "started";
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
