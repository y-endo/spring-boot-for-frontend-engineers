package com.example.quickvote.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PollStateTest {

    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant CLOSES_AT = T0.plusSeconds(600);

    @Nested
    class CanVote {
        private final PollState open = new PollState.Open(CLOSES_AT);

        @Test
        void beforeDeadline() {
            assertThat(open.canVote(CLOSES_AT.minusNanos(1))).isTrue();
        }

        @Test
        void exactlyAtDeadlineIsRejected() {
            assertThat(open.canVote(CLOSES_AT)).isFalse();
        }

        @Test
        void afterDeadline() {
            assertThat(open.canVote(CLOSES_AT.plusNanos(1))).isFalse();
        }

        @Test
        void closedPollRejectsVotesEvenBeforeOriginalDeadline() {
            PollState closed = open.close(T0.plusSeconds(60), CloseReason.HOST_CLOSED);
            assertThat(closed.canVote(T0.plusSeconds(120))).isFalse();
        }
    }

    @Nested
    class Close {
        @Test
        void openBecomesClosedWithReason() {
            PollState closed = new PollState.Open(CLOSES_AT).close(T0, CloseReason.HOST_CLOSED);
            assertThat(closed).isEqualTo(new PollState.Closed(T0, CloseReason.HOST_CLOSED));
        }

        @Test
        void closingTwiceFails() {
            PollState closed = new PollState.Closed(T0, CloseReason.DEADLINE);
            assertThatIllegalStateException()
                    .isThrownBy(() -> closed.close(T0.plusSeconds(1), CloseReason.HOST_CLOSED))
                    .withMessageContaining("すでに終了");
        }
    }
}
