import java.util.*;

void main() {
    // --- List：順序あり・重複あり ---
    List<String> names = new ArrayList<>(List.of("alice", "bob", "alice"));
    IO.println("List = " + names + " size=" + names.size());

    // --- Set：重複なし ---
    Set<String> unique = new HashSet<>(names);
    IO.println("Set size = " + unique.size());

    // --- Map ---
    Map<String, Integer> votes = new HashMap<>();
    for (String n : names) {
        votes.merge(n, 1, Integer::sum);                 // JS の (m[n] ??= 0)++ に近い
    }
    IO.println("Map = " + new TreeMap<>(votes));

    // --- 順序の違い ---
    var hash   = new HashMap<String, Integer>();
    var linked = new LinkedHashMap<String, Integer>();
    var tree   = new TreeMap<String, Integer>();
    for (String k : List.of("zebra", "apple", "mango", "banana")) {
        hash.put(k, k.length());
        linked.put(k, k.length());
        tree.put(k, k.length());
    }
    IO.println("HashMap       = " + hash);
    IO.println("LinkedHashMap = " + linked);
    IO.println("TreeMap       = " + tree);

    // --- computeIfAbsent：Map<K, List<V>> の定番 ---
    Map<Integer, List<String>> byLength = new TreeMap<>();
    for (String k : linked.keySet()) {
        byLength.computeIfAbsent(k.length(), _ -> new ArrayList<>()).add(k);
    }
    IO.println("文字数ごと = " + byLength);

    // --- 不変コレクションの落とし穴 ---
    List<String> fixed = List.of("a", "b");
    try {
        fixed.add("c");
    } catch (UnsupportedOperationException e) {
        IO.println("List.of(...).add → UnsupportedOperationException");
    }

    List<String> original = new ArrayList<>(List.of("a", "b"));
    List<String> view = Collections.unmodifiableList(original);   // 「読み取り専用の窓」
    List<String> copy = List.copyOf(original);                     // 独立した不変コピー
    original.add("c");
    IO.println("view = " + view + " / copy = " + copy);

    // --- Arrays.asList は固定長 ---
    List<String> fixedSize = Arrays.asList("x", "y");
    fixedSize.set(0, "X");                                          // 置換はOK
    try {
        fixedSize.add("z");                                         // 追加はNG
    } catch (UnsupportedOperationException e) {
        IO.println("Arrays.asList(...).add → UnsupportedOperationException / 内容 = " + fixedSize);
    }

    // --- ループ中の削除 ---
    List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4));
    try {
        for (Integer n : nums) {
            if (n == 2) nums.remove(n);
        }
    } catch (ConcurrentModificationException e) {
        IO.println("for-each 中の remove → ConcurrentModificationException");
    }
    nums.removeIf(n -> n % 2 == 0);                                 // こう書く
    IO.println("removeIf 後 = " + nums);
}
