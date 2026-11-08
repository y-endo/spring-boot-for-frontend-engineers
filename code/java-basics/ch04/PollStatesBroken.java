import java.time.Instant;

sealed interface PollState permits Open, Closed, Scheduled {}      // 「開始前」の状態を追加した
record Open(Instant closesAt) implements PollState {}
record Closed(Instant closedAt) implements PollState {}
record Scheduled(Instant opensAt) implements PollState {}

String describe(PollState state) {
    return switch (state) {                              // Scheduled の分岐を書き忘れている
        case Open o   -> "受付中";
        case Closed c -> "終了";
    };
}

void main() {
    IO.println(describe(new Scheduled(Instant.now())));
}
