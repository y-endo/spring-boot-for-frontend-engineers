package com.example.review3.fixed;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.review3.infra.Broadcaster;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class EventHub implements Broadcaster {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        emitters.add(emitter);
        // 終わり方は3通り（正常終了・タイムアウト・エラー）。どれでも名簿から外す
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));   // ヘッダーを確定させる
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public void broadcast(String data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(data);
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);                // 送れない購読者は名簿から外す
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    public int subscriberCount() {
        return emitters.size();
    }
}
