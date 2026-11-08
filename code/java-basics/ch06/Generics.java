import java.util.*;

record Box<T>(T value) {                                // ジェネリクスなレコード
    <R> Box<R> map(java.util.function.Function<T, R> f) {
        return new Box<>(f.apply(value));
    }
}

<T> T firstOrThrow(List<T> list) {                      // ジェネリックメソッド
    if (list.isEmpty()) throw new NoSuchElementException("空です");
    return list.get(0);
}

double sum(List<? extends Number> numbers) {            // 境界付き（読み取り専用の窓）
    double s = 0;
    for (Number n : numbers) s += n.doubleValue();
    return s;
}

void main() {
    Box<String> b = new Box<>("QuickVote");
    Box<Integer> len = b.map(String::length);
    IO.println(b + " → " + len);

    String s = firstOrThrow(List.of("a", "b"));         // キャストなしで String
    IO.println("first = " + s);

    IO.println("sum = " + sum(List.of(1, 2, 3)) + " / " + sum(List.of(1.5, 2.5)));

    // --- 型消去：実行時には <String> も <Integer> も消えている ---
    List<String> strings = new ArrayList<>();
    List<Integer> ints = new ArrayList<>();
    IO.println("同じクラス? " + (strings.getClass() == ints.getClass()));

    // --- 生の型（raw type）に通すとコンパイラの保護をすり抜ける ---
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<String> sneaky = (List) ints;
    ints.add(42);
    try {
        String oops = sneaky.get(0);                    // 取り出し時に初めて ClassCastException
        IO.println(oops);
    } catch (ClassCastException e) {
        IO.println("ClassCastException: " + e.getMessage());
    }

    // --- List<Object> は List<String> の親ではない ---
    // List<Object> objs = new ArrayList<String>();     // ← コンパイルエラー
}
