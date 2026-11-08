import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

void main() {
    // 関数型インターフェースに、ラムダやメソッド参照を代入する
    Function<String, Integer> length = s -> s.length();
    Function<String, Integer> length2 = String::length;           // メソッド参照
    Predicate<Integer> positive = n -> n > 0;
    Supplier<String> now = () -> "supply";

    IO.println(length.apply("QuickVote") + " " + length2.apply("QV") + " " + positive.test(-1) + " " + now.get());

    // 合計は stream で書くのが普通
    List<Integer> nums = List.of(1, 2, 3);
    IO.println("sum = " + nums.stream().mapToInt(Integer::intValue).sum());

    // どうしても外側の変数を更新したいときは、箱（AtomicInteger など）を使う
    var box = new AtomicInteger();
    nums.forEach(box::addAndGet);
    IO.println("box = " + box.get());
}
