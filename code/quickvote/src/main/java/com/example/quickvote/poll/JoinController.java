package com.example.quickvote.poll;

import com.example.quickvote.security.VisitorPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JoinController {

    private final JoinService joinService;

    public JoinController(JoinService joinService) {
        this.joinService = joinService;
    }

    /** 試行回数制限（IP単位・訪問者単位）は、この前段の RateLimitFilter で行う。 */
    @PostMapping("/api/join")
    public JoinResponse join(@AuthenticationPrincipal VisitorPrincipal visitor, @Valid @RequestBody JoinRequest request) {
        return joinService.join(visitor.visitorId(), request.code());
    }
}
