package com.example.review3.bad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.review3.infra.Broadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class EventHub implements Broadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventHub.class);
    private static final List<SseEmitter> EMITTERS = new ArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        EMITTERS.add(emitter);
        return emitter;
    }

    @Override
    public void broadcast(String data) {
        for (SseEmitter emitter : EMITTERS) {
            try {
                emitter.send(data);
            } catch (IOException | IllegalStateException e) {
                log.debug("送信に失敗しました");
            }
        }
    }

    @Override
    public int subscriberCount() {
        return EMITTERS.size();
    }
}
