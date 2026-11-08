package com.example.review2.fixed;

import java.util.Map;

import com.example.review2.infra.CreatePoll;
import com.example.review2.infra.Invites;
import com.example.review2.infra.PollData;
import com.example.review2.infra.PollView;
import com.example.review2.infra.Polls;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PollController {

    private final Polls polls;
    private final Invites invites;

    public PollController(Polls polls, Invites invites) {
        this.polls = polls;
        this.invites = invites;
    }

    @PostMapping("/polls")
    public ResponseEntity<PollView> create(@RequestAttribute(name = "visitorId", required = false) Long visitorId,
            @RequestBody CreatePoll request) {
        PollData poll = polls.create(request.title(), authenticated(visitorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(PollView.of(poll));
    }

    @GetMapping("/polls/{id}")
    public PollView get(@PathVariable long id,
            @RequestAttribute(name = "visitorId", required = false) Long visitorId) {
        return PollView.of(requireMember(id, visitorId));
    }

    @PostMapping("/polls/{id}/close")
    public ResponseEntity<Void> close(@PathVariable long id,
            @RequestAttribute(name = "visitorId", required = false) Long visitorId) {
        requireHost(id, visitorId).close();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/polls/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id,
            @RequestAttribute(name = "visitorId", required = false) Long visitorId) {
        requireHost(id, visitorId);
        polls.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/polls/{id}/invites")
    public Map<String, String> invite(@PathVariable long id,
            @RequestAttribute(name = "visitorId", required = false) Long visitorId) {
        requireHost(id, visitorId);
        return Map.of("token", invites.issue(id));
    }

    @PostMapping("/invites/{token}/accept")
    public PollView accept(@PathVariable String token,
            @RequestAttribute(name = "visitorId", required = false) Long visitorId) {
        long visitor = authenticated(visitorId);
        long pollId = invites.consume(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PollData poll = polls.find(pollId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        poll.addMember(visitor);
        return PollView.of(poll);
    }

    // ---- 認可の入口。各メソッドは、処理の前に必ずどれかを呼ぶ ----

    private static long authenticated(Long visitorId) {
        if (visitorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return visitorId;
    }

    /** 参加者でなければ404。存在しない投票と区別しない（存在を漏らさない）。 */
    private PollData requireMember(long pollId, Long visitorId) {
        long visitor = authenticated(visitorId);
        return polls.find(pollId).filter(p -> p.isMember(visitor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** 参加者だが主催者でなければ403。 */
    private PollData requireHost(long pollId, Long visitorId) {
        PollData poll = requireMember(pollId, visitorId);
        if (poll.hostId() != visitorId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return poll;
    }
}
