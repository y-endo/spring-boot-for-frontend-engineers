package com.example.review1.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/** 本物の HTTP の代わり。外部APIは遅い、という想定で 200ms 待つ。 */
@Component
public class LoggingWebhookClient implements WebhookClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebhookClient.class);

    @Override
    public void notifyJoined(long pollId, long visitorId) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("webhook sent pollId={} visitorId={}", pollId, visitorId);
    }
}
