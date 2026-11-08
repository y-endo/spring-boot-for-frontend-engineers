package com.example.review2.infra;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** メモリ上の投票置き場（演習用。DBの代わり）。 */
@Component
public class Polls {

    private final Map<Long, PollData> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public PollData create(String title, long hostId) {
        PollData poll = new PollData(sequence.incrementAndGet(), title, hostId);
        store.put(poll.id(), poll);
        return poll;
    }

    public Optional<PollData> find(long id) {
        return Optional.ofNullable(store.get(id));
    }

    public void delete(long id) {
        store.remove(id);
    }
}
