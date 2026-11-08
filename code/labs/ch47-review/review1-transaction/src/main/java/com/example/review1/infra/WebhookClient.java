package com.example.review1.infra;

/** 外部システムへの通知（HTTP を想定）。 */
public interface WebhookClient {

    void notifyJoined(long pollId, long visitorId);
}
