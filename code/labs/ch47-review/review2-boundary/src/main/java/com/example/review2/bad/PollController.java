package com.example.review2.bad;

import java.util.Map;

import com.example.review2.infra.CreatePoll;
import com.example.review2.infra.Invites;
import com.example.review2.infra.PollData;
import com.example.review2.infra.PollView;
import com.example.review2.infra.Polls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PollController.class);

    private final Polls polls;
    private final Invites invites;

    public PollController(Polls polls, Invites invites) {
        this.polls = polls;
        this.invites = invites;
    }

    @PostMapping("/polls")
    public ResponseEntity<PollView> create(@RequestAttribute("visitorId") long visitorId,
            @RequestBody CreatePoll request) {
        PollData poll = polls.create(request.title(), visitorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(PollView.of(poll));
    }

    @GetMapping("/polls/{id}")
    public PollView get(@PathVariable long id) {
        return polls.find(id).map(PollView::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/polls/{id}/close")
    public ResponseEntity<Void> close(@PathVariable long id, @RequestAttribute("visitorId") long visitorId) {
        PollData poll = polls.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (poll.hostId() != visitorId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        poll.close();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/polls/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        polls.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/polls/{id}/invites")
    public Map<String, String> invite(@PathVariable long id, @RequestAttribute("visitorId") long visitorId) {
        PollData poll = polls.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (poll.hostId() != visitorId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return Map.of("token", invites.issue(id));
    }

    @PostMapping("/invites/{token}/accept")
    public PollView accept(@PathVariable String token, @RequestAttribute("visitorId") long visitorId) {
        log.info("招待を受諾します token={} visitor={}", token, visitorId);
        long pollId = invites.consume(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PollData poll = polls.find(pollId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        poll.addMember(visitorId);
        return PollView.of(poll);
    }
}
