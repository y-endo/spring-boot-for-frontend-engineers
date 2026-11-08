package com.example.quickvote.poll;

import java.time.Instant;

/** 投票の状態。Open（受付中）か Closed（終了）のどちらかしかない。 */
public sealed interface PollState {

    record Open(Instant closesAt) implements PollState {}

    record Closed(Instant closedAt, CloseReason reason) implements PollState {}

    /** 終了させる。すでに終了している投票を再度終了させることはできない。 */
    default PollState close(Instant now, CloseReason reason) {
        return switch (this) {
            case Open _ -> new Closed(now, reason);
            case Closed c -> throw new IllegalStateException("すでに終了しています: " + c.closedAt());
        };
    }

    /** 投票できるか。締切ちょうどの瞬間は受け付けない（closesAt の直前まで）。 */
    default boolean canVote(Instant now) {
        return switch (this) {
            case Open(Instant closesAt) -> now.isBefore(closesAt);
            case Closed _ -> false;
        };
    }
}
