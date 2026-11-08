import java.time.Instant;

enum CloseReason { DEADLINE, HOST_CLOSED }

sealed interface PollState permits Open, Closed {}
record Open(Instant closesAt) implements PollState {}
record Closed(Instant closedAt, CloseReason reason) implements PollState {}

String describe(PollState state) {
    return switch (state) {                              // default がない＝全パターンを書く約束
        case Open o   -> "受付中（締切 " + o.closesAt() + "）";
        case Closed c -> "終了（" + c.reason() + " / " + c.closedAt() + "）";
    };
}

PollState close(PollState state, Instant now, CloseReason reason) {
    return switch (state) {
        case Open _   -> new Closed(now, reason);        // 使わない変数は _ と書ける
        case Closed c -> throw new IllegalStateException("すでに終了しています: " + c.closedAt());
    };
}

boolean canVote(PollState state, Instant now) {
    return switch (state) {
        case Open(Instant closesAt) when now.isBefore(closesAt) -> true;   // レコードパターン＋ガード
        case Open _   -> false;                          // 締切時刻を過ぎている
        case Closed _ -> false;
    };
}

void main() {
    var t0 = Instant.parse("2026-11-08T10:00:00Z");
    PollState s = new Open(t0.plusSeconds(600));

    IO.println(describe(s));
    IO.println("10:05 に投票できる? " + canVote(s, t0.plusSeconds(300)));
    IO.println("10:11 に投票できる? " + canVote(s, t0.plusSeconds(660)));

    s = close(s, t0.plusSeconds(120), CloseReason.HOST_CLOSED);
    IO.println(describe(s));
    IO.println("終了後に投票できる? " + canVote(s, t0.plusSeconds(130)));

    try {
        close(s, t0.plusSeconds(200), CloseReason.HOST_CLOSED);
    } catch (IllegalStateException e) {
        IO.println("2回目の close = " + e.getMessage());
    }
}
