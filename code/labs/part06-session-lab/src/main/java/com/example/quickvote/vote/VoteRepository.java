package com.example.quickvote.vote;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

@Repository
public class VoteRepository {

    /** UNIQUE(poll_id, participant_id) に当たるキー。同じ人の票は、Map の中でも1つしか持てない。 */
    private record Key(long pollId, long participantId) {
    }

    private record Ballot(long optionId, Instant votedAt) {
    }

    private final ConcurrentHashMap<Key, Ballot> ballots = new ConcurrentHashMap<>();

    /**
     * 投票（上書き）。戻り値は完成形と同じ意味で、「票が実際に変わったか」を件数（1 か 0）で返す。
     * <ul>
     * <li>初めての投票、または別の選択肢への変更 → 1</li>
     * <li>同じ選択肢への再投票 → 0（PUT が冪等であることの現れ）</li>
     * </ul>
     */
    public int upsert(long pollId, long participantId, long optionId, Instant now) {
        AtomicInteger changed = new AtomicInteger();
        // compute は「読む→判断→書く」を、このキーについて分割できない1操作で行う。
        ballots.compute(new Key(pollId, participantId), (key, old) -> {
            if (old != null && old.optionId() == optionId) {
                return old; // 同じ選択肢への再投票。何も変えない
            }
            changed.set(1);
            return new Ballot(optionId, now);
        });
        return changed.get();
    }

    public Optional<Long> findOptionId(long pollId, long participantId) {
        return Optional.ofNullable(ballots.get(new Key(pollId, participantId))).map(Ballot::optionId);
    }

    /** この選択肢の得票数。第3部では SQL の count(...) GROUP BY に置き換わり、このメソッドは消える。 */
    public int countByOption(long pollId, long optionId) {
        return (int) ballots.entrySet().stream()
                .filter(e -> e.getKey().pollId() == pollId && e.getValue().optionId() == optionId)
                .count();
    }
}
