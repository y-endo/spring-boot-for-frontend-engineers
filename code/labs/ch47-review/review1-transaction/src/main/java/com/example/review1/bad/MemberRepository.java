package com.example.review1.bad;

import com.example.review1.infra.MemberStore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MemberRepository implements MemberStore {

    private final JdbcClient jdbc;

    MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countParticipants(long pollId) {
        return jdbc.sql("SELECT count(*) FROM participants WHERE poll_id = :pollId")
                .param("pollId", pollId)
                .query(Integer.class)
                .single();
    }

    void insertParticipant(long pollId, long visitorId) {
        jdbc.sql("INSERT INTO participants (poll_id, visitor_id) VALUES (:pollId, :visitorId)")
                .param("pollId", pollId)
                .param("visitorId", visitorId)
                .update();
    }

    void insertOption(long pollId, String label) {
        jdbc.sql("INSERT INTO options (poll_id, label) VALUES (:pollId, :label)")
                .param("pollId", pollId)
                .param("label", label)
                .update();
    }

    void markClosed(long pollId) {
        jdbc.sql("UPDATE polls SET status = 'CLOSED' WHERE id = :pollId")
                .param("pollId", pollId)
                .update();
    }
}
