package com.example.quickvote.poll;

import java.time.Clock;
import java.time.Instant;

/**
 * 「今、投票を受け付けているか」を答える。
 * 現在時刻は Instant.now() を直接呼ばず、Clock を受け取る（テストで時間を固定できるようにするため）。
 */
public final class VotingWindow {

    private final Clock clock;
    private final Instant closesAt;

    public VotingWindow(Clock clock, Instant closesAt) {
        this.clock = clock;
        this.closesAt = closesAt;
    }

    public boolean isOpen() {
        return Instant.now(clock).isBefore(closesAt);
    }
}
