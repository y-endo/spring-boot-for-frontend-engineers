import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

record Vote(String user, String option, int weight) {}

void main() {
    List<Vote> votes = List.of(
            new Vote("a", "ラーメン", 1), new Vote("b", "カレー", 1),
            new Vote("c", "ラーメン", 2), new Vote("d", "そば", 1));

    // votes.map(v => v.option)
    IO.println(votes.stream().map(Vote::option).toList());
    // votes.filter(v => v.weight > 1)
    IO.println(votes.stream().filter(v -> v.weight() > 1).toList());
    // votes.reduce((s, v) => s + v.weight, 0)
    IO.println(votes.stream().mapToInt(Vote::weight).sum());
    // votes.find(v => v.option === "そば")
    IO.println(votes.stream().filter(v -> v.option().equals("そば")).findFirst());
    // votes.some(...) / votes.every(...)
    IO.println(votes.stream().anyMatch(v -> v.weight() > 1) + " / " + votes.stream().allMatch(v -> v.weight() > 1));
    // Object.groupBy(votes, v => v.option)
    Map<String, List<Vote>> grouped = votes.stream().collect(Collectors.groupingBy(Vote::option));
    IO.println(new TreeMap<>(grouped).keySet());
    // 票数の集計: groupBy + sum
    Map<String, Integer> tally = votes.stream()
            .collect(Collectors.groupingBy(Vote::option, TreeMap::new, Collectors.summingInt(Vote::weight)));
    IO.println(tally);
    // flatMap
    IO.println(List.of(List.of(1, 2), List.of(3)).stream().flatMap(List::stream).toList());
    // [...new Set(arr)]
    IO.println(List.of(1, 2, 2, 3, 3).stream().distinct().toList());
    // arr.sort((a, b) => a - b) / toSorted((a, b) => b - a)
    List<Integer> nums = new ArrayList<>(List.of(3, 1, 2));
    nums.sort(Comparator.naturalOrder());
    IO.println(nums + " / " + List.of(3, 1, 2).stream().sorted(Comparator.reverseOrder()).toList());
    // arr[0] / arr.at(-1)
    IO.println(nums.getFirst() + " / " + nums.getLast());
    // arr.slice(1, 3)
    IO.println(nums.subList(1, 3));
    // arr.join(", ")
    IO.println(String.join(", ", List.of("a", "b")));
    // Array.from({length: 3}, (_, i) => i * 2)
    IO.println(IntStream.range(0, 3).map(i -> i * 2).boxed().toList());
    // Map: map.set("x", (map.get("x") ?? 0) + 1) など
    Map<String, Integer> m = new HashMap<>();
    m.put("x", 1);
    m.merge("x", 1, Integer::sum);
    m.computeIfAbsent("y", k -> 10);
    IO.println(new TreeMap<>(m) + " / " + m.getOrDefault("z", 0) + " / " + m.containsKey("x"));
    // for (const [k, v] of map)
    for (var e : new TreeMap<>(m).entrySet()) IO.print(e.getKey() + "=" + e.getValue() + ";");
    IO.println("");
    // Set 演算（積集合）
    Set<Integer> s1 = new TreeSet<>(List.of(1, 2, 3)), s2 = Set.of(2, 3, 4);
    Set<Integer> inter = new TreeSet<>(s1);
    inter.retainAll(s2);
    IO.println(inter);
    // スプレッド: [...a, ...b]
    List<Integer> joined = new ArrayList<>(List.of(1));
    joined.addAll(List.of(2, 3));
    IO.println(joined);
    // Object.freeze 相当
    try {
        List.of(1).add(2);
    } catch (UnsupportedOperationException e) {
        IO.println("List.of is immutable");
    }
}
