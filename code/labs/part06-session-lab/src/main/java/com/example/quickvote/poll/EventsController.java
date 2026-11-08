package com.example.quickvote.poll;

import java.io.IOException;
import java.util.UUID;

import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.security.VisitorPrincipal;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 第32章の実験用の、ごく小さな SSE。接続時に認可し、1件送って、0.3秒後に閉じる。
 * 本物の SSE は第7部（イベント履歴・Last-Event-ID・複数台）。ここで見たいのは「接続が終わるときの認証」だけ。
 */
@RestController
public class EventsController {

    private final ParticipantService access;
    private final long CLOSE_AFTER_MILLIS = Long.getLong("lab.sse-close-after-millis", 300);

    public EventsController(ParticipantService access) {
        this.access = access;
    }

    @GetMapping(value = "/api/polls/{pollId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId)
            throws IOException {
        access.require(pollId, visitor.visitorId()); // 参加者だけ。非参加者は 404
        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().name("hello").data("connected"));
        // 少し後に別スレッドで接続を終える。終わるとき、コンテナが「非同期ディスパッチ」(DispatcherType.ASYNC) で
        // フィルタチェーンをもう一度通す。Thread.sleep の代わりに、完成形ではハートビートやタイムアウトが閉じる。
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(CLOSE_AFTER_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            emitter.complete();
        });
        return emitter;
    }
}
