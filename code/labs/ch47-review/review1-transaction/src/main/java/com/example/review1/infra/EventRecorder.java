package com.example.review1.infra;

/** イベント履歴（poll_events）への記録。 */
public interface EventRecorder {

    void record(long pollId, String type);
}
