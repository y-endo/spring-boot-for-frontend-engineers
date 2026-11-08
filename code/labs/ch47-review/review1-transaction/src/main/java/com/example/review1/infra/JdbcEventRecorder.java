package com.example.review1.infra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcEventRecorder implements EventRecorder {

    private final JdbcClient jdbc;

    public JdbcEventRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(long pollId, String type) {
        jdbc.sql("INSERT INTO poll_events (poll_id, type) VALUES (:pollId, :type)")
                .param("pollId", pollId)
                .param("type", type)
                .update();
    }
}
