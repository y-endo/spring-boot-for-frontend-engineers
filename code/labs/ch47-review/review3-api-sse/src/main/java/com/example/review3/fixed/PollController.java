package com.example.review3.fixed;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final JdbcClient jdbc;

    public PollController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    public PollView get(@PathVariable long id) {
        return jdbc.sql("SELECT id, title FROM polls WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new PollView(rs.getLong("id"), rs.getString("title")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{id}/options")
    public ResponseEntity<Map<String, Object>> addOption(@PathVariable long id,
            @Valid @RequestBody OptionRequest request) {
        jdbc.sql("INSERT INTO options (poll_id, label) VALUES (:pollId, :label)")
                .param("pollId", id)
                .param("label", request.label())
                .update();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
    }
}
