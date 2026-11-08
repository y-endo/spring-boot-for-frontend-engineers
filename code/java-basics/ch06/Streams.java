import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

record Vote(String voter, String option) {}

void main() {
    List<Vote> votes = List.of(
        new Vote("alice", "カレー"), new Vote("bob", "ラーメン"), new Vote("carol", "カレー"),
        new Vote("dave", "カレー"), new Vote("erin", "そば"), new Vote("frank", "ラーメン"));

    // --- map / filter ---
    List<String> curryVoters = votes.stream()
        .filter(v -> v.option().equals("カレー"))
        .map(Vote::voter)
        .toList();                                       // 不変リスト
    IO.println("カレー派 = " + curryVoters);

    // --- reduce 相当 ---
    int totalLength = votes.stream().mapToInt(v -> v.voter().length()).sum();
    IO.println("名前の文字数合計 = " + totalLength);

    // --- 集計：option ごとの票数（JS の reduce で作る Record<string, number> に相当） ---
    Map<String, Long> tally = votes.stream()
        .collect(Collectors.groupingBy(Vote::option, Collectors.counting()));
    IO.println("集計 = " + new TreeMap<>(tally));

    // --- 票数の降順で並べる ---
    tally.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .forEach(e -> IO.println("  " + e.getKey() + " : " + e.getValue()));

    // --- 遅延評価と、1回きり ---
    Stream<Integer> s = Stream.of(1, 2, 3).peek(n -> IO.println("  peek " + n));
    IO.println("Stream を作っただけ（まだ何も実行されていない）");
    s.toList();
    try {
        s.toList();
    } catch (IllegalStateException e) {
        IO.println("2回目 → " + e.getMessage());
    }

    // --- 同じ処理を for で ---
    Map<String, Integer> byFor = new TreeMap<>();
    for (Vote v : votes) {
        byFor.merge(v.option(), 1, Integer::sum);
    }
    IO.println("for 版 = " + byFor);

    // --- IntStream ---
    IO.println("1..5 の二乗和 = " + IntStream.rangeClosed(1, 5).map(n -> n * n).sum());
}
