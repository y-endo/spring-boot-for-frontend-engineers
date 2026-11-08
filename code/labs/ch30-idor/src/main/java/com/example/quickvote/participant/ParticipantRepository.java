package com.example.quickvote.participant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.example.quickvote.poll.Poll;
import com.example.quickvote.poll.PollRepository;

import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepository {

    public record Participant(long id, long pollId, long visitorId, ParticipantRole role) {
    }

    /** 保存される1行。joinedAt は完成形の participants.joined_at に当たる。 */
    private record Row(Participant participant, Instant joinedAt) {
    }

    private final PollRepository polls;
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Row> byId = new ConcurrentHashMap<>();

    public ParticipantRepository(PollRepository polls) {
        this.polls = polls;
    }

    /**
     * 公開ID(UUID)と訪問者から、参加者としての文脈を探す。
     * 「投票が存在しない」と「存在するが参加していない」は、どちらも空になる。
     * 呼び出し側はどちらも同じ404にするので、存在の有無が外に漏れない。
     */
    public Optional<ParticipantContext> findActive(UUID pollPublicId, long visitorId) {
        Optional<Poll> poll = polls.findByPublicId(pollPublicId);
        if (poll.isEmpty()) {
            return Optional.empty();
        }
        return findByPollAndVisitor(poll.get().id(), visitorId)
                .map(p -> new ParticipantContext(p.pollId(), pollPublicId, p.id(), p.role()));
    }

    public Optional<Participant> findByPollAndVisitor(long pollId, long visitorId) {
        return byId.values().stream()
                .map(Row::participant)
                .filter(p -> p.pollId() == pollId && p.visitorId() == visitorId)
                .findFirst();
    }

    public int countActive(long pollId) {
        return (int) byId.values().stream().filter(r -> r.participant().pollId() == pollId).count();
    }

    public long insert(long pollId, long visitorId, ParticipantRole role, Instant now) {
        long id = sequence.incrementAndGet();
        byId.put(id, new Row(new Participant(id, pollId, visitorId, role), now));
        return id;
    }
}
