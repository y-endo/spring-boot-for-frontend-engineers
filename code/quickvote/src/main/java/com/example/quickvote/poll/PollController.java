package com.example.quickvote.poll;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.IdempotencyService;
import com.example.quickvote.security.VisitorPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    /** Idempotency-Key は印字可能なASCII 1〜200文字（UUID を想定。空白や制御文字は不可）。 */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[\\x21-\\x7E]{1,200}");

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @PostMapping
    public ResponseEntity<PollDetail> create(@AuthenticationPrincipal VisitorPrincipal visitor,
            @Valid @RequestBody CreatePollRequest request) {
        PollDetail detail = pollService.create(visitor.visitorId(), request);
        return ResponseEntity.created(URI.create("/api/polls/" + detail.id())).body(detail);
    }

    @GetMapping("/{pollId}")
    public PollDetail get(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId) {
        return pollService.get(visitor.visitorId(), pollId);
    }

    @PostMapping("/{pollId}/options")
    public ResponseEntity<OptionResponse> addOption(@AuthenticationPrincipal VisitorPrincipal visitor,
            @PathVariable UUID pollId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AddOptionRequest request) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        IdempotencyService.Result<OptionResponse> result =
                pollService.addOption(visitor.visitorId(), pollId, idempotencyKey, request.label());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        if (result.replayed()) {
            // 保存済みの応答を返したことを知らせる。クライアントは無視してよい（調査・デバッグ用）。
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.body());
    }

    @PostMapping("/{pollId}/close")
    public ResponseEntity<Void> close(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId) {
        pollService.close(visitor.visitorId(), pollId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{pollId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId) {
        pollService.delete(visitor.visitorId(), pollId);
        return ResponseEntity.noContent().build();
    }
}
