package com.example.quickvote.poll;

import java.net.URI;
import java.util.UUID;

import com.example.quickvote.security.VisitorPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @PostMapping
    public ResponseEntity<PollDetail> create(VisitorPrincipal visitor, @Valid @RequestBody CreatePollRequest request) {
        PollDetail detail = pollService.create(visitor.visitorId(), request);
        return ResponseEntity.created(URI.create("/api/polls/" + detail.id())).body(detail);
    }

    @GetMapping("/{pollId}")
    public PollDetail get(VisitorPrincipal visitor, @PathVariable UUID pollId) {
        return pollService.get(visitor.visitorId(), pollId);
    }

    /** 完成形では Idempotency-Key ヘッダーが必須になる（第5部）。ここではまだ受け取らない。 */
    @PostMapping("/{pollId}/options")
    public ResponseEntity<OptionResponse> addOption(VisitorPrincipal visitor, @PathVariable UUID pollId,
            @Valid @RequestBody AddOptionRequest request) {
        OptionResponse created = pollService.addOption(visitor.visitorId(), pollId, request.label());
        return ResponseEntity.status(201).body(created);
    }
}
