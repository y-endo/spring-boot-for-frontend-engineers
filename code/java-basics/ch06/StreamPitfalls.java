import java.util.*;
import java.util.stream.*;

record Vote(String voter, String option) {}

void main() {
    List<Vote> votes = List.of(new Vote("alice", "カレー"), new Vote("alice", "ラーメン"));

    // toMap はキーが重複すると例外
    try {
        votes.stream().collect(Collectors.toMap(Vote::voter, Vote::option));
    } catch (IllegalStateException e) {
        IO.println("toMap → " + e.getMessage());
    }
    // 重複したときの扱いを指定する（後勝ち）
    Map<String, String> last = votes.stream().collect(Collectors.toMap(Vote::voter, Vote::option, (a, b) -> b));
    IO.println("後勝ち = " + last);

    // toList() は不変、Collectors.toList() は（実装上）可変
    List<String> a = votes.stream().map(Vote::voter).toList();
    try {
        a.add("x");
    } catch (UnsupportedOperationException e) {
        IO.println("Stream.toList() は不変");
    }

    // 空のリストの get(0)
    try {
        new ArrayList<String>().get(0);
    } catch (IndexOutOfBoundsException e) {
        IO.println("get(0) → " + e.getMessage());
    }
}
