import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

// TS: type Shape = { kind: "circle", r: number } | { kind: "rect", w: number, h: number }
sealed interface Shape permits Circle, Rect {}
record Circle(double r) implements Shape {}
record Rect(double w, double h) implements Shape {}

// TS: enum PollStatus { OPEN, CLOSED }
enum PollStatus { OPEN, CLOSED }

// TS: type Point = { readonly x: number; readonly y: number }
record Point(int x, int y) {}

// TS: type Pair<A, B> = [A, B]
record Pair<A, B>(A first, B second) {}

// TS: function max<T extends Comparable<T>>(a: T, b: T): T
<T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}

double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Rect(double w, double h) -> w * h;      // record パターン（分割代入に近い）
    };
}

void main() {
    int i = 42;                                   // number（整数）
    long big = 1L << 40;                          // number（大きい整数）
    double d = 0.1 + 0.2;                         // number（浮動小数点）
    BigDecimal exact = new BigDecimal("0.1").add(new BigDecimal("0.2"));
    BigInteger huge = BigInteger.TWO.pow(100);    // bigint
    IO.println(i + " / " + big + " / " + d + " / " + exact + " / " + huge);

    Object anything = "hello";                    // unknown に近い（使うときに絞り込みが必要）
    if (anything instanceof String s && s.length() > 3) {   // 型ガード + 絞り込み
        IO.println("String of length " + s.length());
    }

    IO.println(area(new Circle(1.0)) + " / " + area(new Rect(2, 3)));
    IO.println(new Point(1, 2) + " / " + new Point(1, 2).equals(new Point(1, 2)));
    IO.println(new Pair<>("a", 1));
    IO.println(max(3, 7) + " / " + max("apple", "banana"));
    IO.println(PollStatus.valueOf("OPEN") + " / " + List.of(PollStatus.values()));

    List<Map<String, Integer>> nested = List.of(Map.of("votes", 3));   // Array<Record<string, number>>
    IO.println(nested);
}
