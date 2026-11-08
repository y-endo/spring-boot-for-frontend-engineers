package com.example.quickvote.poll;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.example.quickvote.vote.VoteRepository;

import org.springframework.stereotype.Repository;

@Repository
public class OptionRepository {

    /** 選択肢と、その得票数。 */
    public record OptionCount(long id, String label, int votes) {
    }

    private record Row(long id, long pollId, String label, long createdBy, Instant createdAt) {
    }

    private final VoteRepository votes;
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Row> byId = new ConcurrentHashMap<>();

    public OptionRepository(VoteRepository votes) {
        this.votes = votes;
    }

    public long insert(long pollId, String label, long createdBy, Instant now) {
        long id = sequence.incrementAndGet();
        byId.put(id, new Row(id, pollId, label, createdBy, now));
        return id;
    }

    public int count(long pollId) {
        return (int) byId.values().stream().filter(r -> r.pollId() == pollId).count();
    }

    public boolean existsInPoll(long pollId, long optionId) {
        Row row = byId.get(optionId);
        return row != null && row.pollId() == pollId;
    }

    /** 票が0の選択肢も 0 件として返す。完成形では LEFT JOIN + GROUP BY が行う仕事。 */
    public List<OptionCount> findWithVoteCounts(long pollId) {
        return byId.values().stream()
                .filter(r -> r.pollId() == pollId)
                .sorted(Comparator.comparingLong(Row::id))
                .map(r -> new OptionCount(r.id(), r.label(), votes.countByOption(pollId, r.id())))
                .toList();
    }
}
