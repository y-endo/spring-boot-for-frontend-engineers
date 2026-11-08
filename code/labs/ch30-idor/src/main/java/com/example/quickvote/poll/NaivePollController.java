package com.example.quickvote.poll;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.security.VisitorPrincipal;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第30章の実験用。「わざと認可を忘れた」API と、その直し方を並べる。idor プロファイルのときだけ有効。
 * <ul>
 * <li>v0: 内部ID（1, 2, 3...）で引くだけ。誰が呼んでも中身を返す。IDOR（Insecure Direct Object Reference）</li>
 * <li>v1: 同じ形で、参加者かどうかを確かめてから返す</li>
 * </ul>
 * 本線のコード（PollController）には、こういう API を作ってはいけない。
 */
@RestController
@Profile("idor")
public class NaivePollController {

    private final PollRepository polls;
    private final ParticipantRepository participants;

    public NaivePollController(PollRepository polls, ParticipantRepository participants) {
        this.polls = polls;
        this.participants = participants;
    }

    /** 悪い例。visitor を受け取っているのに、使っていない。 */
    @GetMapping("/api/v0/polls/{id}")
    public Map<String, Object> getV0(VisitorPrincipal visitor, @PathVariable long id) {
        Poll poll = polls.findById(id).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        return summary(poll);
    }

    /** 直した例。参加者でなければ、存在しない投票と同じ 404 にする。 */
    @GetMapping("/api/v1/polls/{id}")
    public Map<String, Object> getV1(VisitorPrincipal visitor, @PathVariable long id) {
        Poll poll = polls.findById(id).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        participants.findByPollAndVisitor(poll.id(), visitor.visitorId())
                .orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        return summary(poll);
    }

    private static Map<String, Object> summary(Poll poll) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", poll.id());
        body.put("title", poll.title());
        body.put("joinCode", poll.joinCode());
        return body;
    }
}
