import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// TS: const add = (a: number, b: number) => a + b
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;

// TS: function greet(name: string, greeting = "Hello")  → オーバーロードで代用
String greet(String name) { return greet(name, "Hello"); }
String greet(String name, String greeting) { return greeting + ", " + name; }

// TS: function sum(...nums: number[])
int sum(int... nums) {
    int total = 0;
    for (int n : nums) total += n;
    return total;
}

// TS: function apply<T, R>(x: T, f: (t: T) => R): R
<T, R> R apply(T x, Function<T, R> f) { return f.apply(x); }

record Range(int from, int to) {}

void main() {
    IO.println(add.apply(2, 3));
    IO.println(greet("QuickVote") + " / " + greet("QuickVote", "Hi"));
    IO.println(sum() + " / " + sum(1, 2, 3));
    IO.println(apply("abc", String::length));

    // 分割代入: const { from, to } = range  →  record パターン
    Object o = new Range(1, 5);
    if (o instanceof Range(int from, int to)) {
        IO.println(from + ".." + to);
    }

    // クロージャ: 捕まえられるのは「実質 final」な変数だけ
    int base = 10;
    Function<Integer, Integer> plusBase = x -> x + base;
    // base++;   // ← コメントを外すとコンパイルエラー（ラムダ式から参照されるローカル変数は...）
    IO.println(plusBase.apply(5));

    // 関数を返す関数（カリー化）
    Function<Integer, Function<Integer, Integer>> curried = a -> b -> a * b;
    IO.println(curried.apply(6).apply(7));

    Predicate<String> isBlank = String::isBlank;
    Supplier<List<String>> mk = ArrayList::new;
    List<String> l = mk.get();
    l.add("x");
    IO.println(isBlank.test(" ") + " / " + l);
}
