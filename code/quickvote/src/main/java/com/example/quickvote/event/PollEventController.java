package com.example.quickvote.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.security.VisitorPrincipal;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE（Server-Sent Events）。参加者だけが購読できる。
 * ブラウザの EventSource は切断されると自動で再接続し、最後に受け取った id を Last-Event-ID ヘッダーで送ってくる。
 */
@RestController
public class PollEventController {

    /** ブラウザが切断後に再接続するまでの待ち時間（ミリ秒）。 */
    private static final long RECONNECT_MILLIS = 3000;

    private final ParticipantService participants;
    private final PollEventHub hub;
    private final QuickVoteProperties properties;

    public PollEventController(ParticipantService participants, PollEventHub hub, QuickVoteProperties properties) {
        this.participants = participants;
        this.hub = hub;
        this.properties = properties;
    }

    @GetMapping(path = "/api/polls/{pollId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventIdHeader) {
        // 認可は接続時に1回。参加者でなければここで404（投票の存在も漏らさない）。
        ParticipantContext context = participants.require(pollId, visitor.visitorId());

        SseEmitter emitter = new SseEmitter(properties.sse().timeout().toMillis());
        // 接続直後にコメント行と retry（再接続までの待ち時間）を送る。
        // 何か書き込まないとレスポンスヘッダーが送られず、ブラウザの EventSource は
        // 最初のイベントが来るまで「接続中」のままになる（onopen が呼ばれない）。
        sendOrFail(emitter, SseEmitter.event().comment("connected").reconnectTime(RECONNECT_MILLIS));
        PollEventHub.Subscription subscription = hub.subscribe(context.pollId(), parseLastEventId(lastEventIdHeader),
                new SseSink(emitter));
        // 切断・タイムアウト・エラーのいずれでも名簿から外す。外し忘れると、死んだ接続が溜まり続ける。
        emitter.onCompletion(() -> hub.unsubscribe(subscription));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> hub.unsubscribe(subscription));
        return emitter;
    }

    private static void sendOrFail(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 数値でない Last-Event-ID は無かったものとして扱う（クライアントの不具合で接続できなくなるのを避ける）。 */
    static Long parseLastEventId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(header.strip());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** イベントを SSE の形式（id / event / data）にして送る。heartbeat はコメント行（": heartbeat"）。 */
    private record SseSink(SseEmitter emitter) implements PollEventHub.EventSink {
        @Override
        public void send(PollEvent event) throws IOException {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.seq()))
                    .name(event.type())
                    .data(event.payload()));
        }

        @Override
        public void heartbeat() throws IOException {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        }

        @Override
        public void complete() {
            emitter.complete();
        }
    }
}
