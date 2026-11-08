package com.example.review3.bad;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final JdbcClient jdbc;

    public PollController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    public PollRow get(@PathVariable long id) {
        return jdbc.sql("SELECT * FROM polls WHERE id = :id")
                .param("id", id)
                .query(PollRow.class)
                .single();
    }

    @PostMapping("/{id}/options")
    public ResponseEntity<Map<String, Object>> addOption(@PathVariable long id,
            @RequestBody Map<String, String> body) {
        jdbc.sql("INSERT INTO options (poll_id, label) VALUES (:pollId, :label)")
                .param("pollId", id)
                .param("label", body.get("label"))
                .update();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
    }
}
