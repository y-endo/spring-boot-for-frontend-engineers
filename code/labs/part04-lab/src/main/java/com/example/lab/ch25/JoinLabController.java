package com.example.lab.ch25;

import java.util.ArrayList;
import java.util.List;

import com.example.lab.LabRepository;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 負荷スクリプト（LoadJoin.java）から叩く実験用API。 */
@RestController
@RequestMapping("/lab/ch25")
public class JoinLabController {

    public record Seed(long pollId, List<Long> candidateVisitorIds) {
    }

    public record Result(long participants) {
    }

    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final LabRepository repo;
    private final TransactionTemplate transaction;
    private final JoinServices.Unsafe unsafe;
    private final JoinServices.TransactionOnly transactionOnly;
    private final JoinServices.Locked locked;

    public JoinLabController(LabRepository repo, TransactionTemplate transaction, JoinServices.Unsafe unsafe,
            JoinServices.TransactionOnly transactionOnly, JoinServices.Locked locked) {
        this.repo = repo;
        this.transaction = transaction;
        this.unsafe = unsafe;
        this.transactionOnly = transactionOnly;
        this.locked = locked;
    }

    /** 参加者が members 人（主催者を含む）の投票と、これから参加する候補者 candidates 人を作る。 */
    @PostMapping("/seed")
    public Seed seed(@RequestParam(defaultValue = "49") int members, @RequestParam(defaultValue = "20") int candidates) {
        return transaction.execute(status -> {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                code.append(CODE_CHARS.charAt((int) (Math.random() * CODE_CHARS.length())));
            }
            long pollId = repo.insertPoll("負荷実験", code.toString());
            repo.insertParticipant(pollId, repo.insertVisitor(), "HOST");
            for (int i = 1; i < members; i++) {
                repo.insertParticipant(pollId, repo.insertVisitor(), "MEMBER");
            }
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < candidates; i++) {
                ids.add(repo.insertVisitor());
            }
            return new Seed(pollId, ids);
        });
    }

    @PostMapping("/join/{mode}")
    public String join(@PathVariable String mode, @RequestParam long pollId, @RequestParam long visitorId) {
        switch (mode) {
            case "unsafe" -> unsafe.join(pollId, visitorId);
            case "tx-only" -> transactionOnly.join(pollId, visitorId);
            case "locked" -> locked.join(pollId, visitorId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode = unsafe | tx-only | locked");
        }
        return "joined";
    }

    @GetMapping("/count")
    public Result count(@RequestParam long pollId) {
        return new Result(repo.countParticipants(pollId));
    }
}
