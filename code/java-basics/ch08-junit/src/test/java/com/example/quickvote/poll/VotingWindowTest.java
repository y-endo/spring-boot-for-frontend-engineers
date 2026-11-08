package com.example.quickvote.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class VotingWindowTest {

    private static final Instant CLOSES_AT = Instant.parse("2026-11-08T14:30:00Z");

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC); // 時刻が止まった時計
    }

    @Test
    void openBeforeDeadline() {
        var window = new VotingWindow(at("2026-11-08T14:29:59Z"), CLOSES_AT);
        assertThat(window.isOpen()).isTrue();
    }

    @Test
    void closedAtDeadline() {
        var window = new VotingWindow(at("2026-11-08T14:30:00Z"), CLOSES_AT);
        assertThat(window.isOpen()).isFalse();
    }

    @Test
    void closedAfterDeadline() {
        var window = new VotingWindow(at("2026-11-08T14:30:01Z"), CLOSES_AT);
        assertThat(window.isOpen()).isFalse();
    }
}
