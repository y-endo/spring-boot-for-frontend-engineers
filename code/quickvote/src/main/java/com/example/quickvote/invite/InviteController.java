package com.example.quickvote.invite;

import java.util.UUID;

import com.example.quickvote.poll.JoinResponse;
import com.example.quickvote.security.VisitorPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping("/api/polls/{pollId}/invites")
    public ResponseEntity<InviteResponse> issue(@AuthenticationPrincipal VisitorPrincipal visitor,
            @PathVariable UUID pollId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteService.issue(visitor.visitorId(), pollId));
    }

    /**
     * 招待の受諾。メールやチャットのリンクを開いただけで消費されないよう、GET ではなく POST にしている
     * （リンクのプレビュー機能や先読みが GET を送っても、招待が消えない）。
     */
    @PostMapping("/api/invites/{token}/accept")
    public JoinResponse accept(@AuthenticationPrincipal VisitorPrincipal visitor, @PathVariable String token) {
        return inviteService.accept(visitor.visitorId(), token);
    }
}
