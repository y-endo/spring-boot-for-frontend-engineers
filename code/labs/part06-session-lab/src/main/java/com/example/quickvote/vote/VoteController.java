package com.example.quickvote.vote;

import java.util.UUID;

import com.example.quickvote.security.VisitorPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PutMapping("/api/polls/{pollId}/my-vote")
    public VoteResponse vote(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable UUID pollId,
            @Valid @RequestBody VoteRequest request) {
        return voteService.vote(visitor.visitorId(), pollId, request.optionId());
    }
}
