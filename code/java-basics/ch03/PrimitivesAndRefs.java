import java.util.ArrayList;
import java.util.List;

class Counter {
    int count;          // プリミティブのフィールド：何も入れなければ 0
    Integer boxed;      // 参照型のフィールド：何も入れなければ null
    String label;       // 同上
}

void addOne(int n)           { n = n + 1; }                 // 呼び出し元には影響しない
void addItem(List<String> l) { l.add("追加された"); }         // 参照が指す先を変更する
void replace(List<String> l) { l = new ArrayList<>(); l.add("別物"); }  // 手元の参照を差し替えるだけ

void main() {
    // --- フィールドの初期値 ---
    Counter c = new Counter();
    IO.println("int=" + c.count + ", Integer=" + c.boxed + ", String=" + c.label);

    // --- 値渡し ---
    int n = 10;
    addOne(n);
    IO.println("addOne 後の n = " + n);

    List<String> list = new ArrayList<>();
    addItem(list);
    IO.println("addItem 後の list = " + list);
    replace(list);
    IO.println("replace 後の list = " + list);

    // --- 別名（エイリアス） ---
    List<String> a = new ArrayList<>(List.of("A"));
    List<String> b = a;                 // コピーではなく、同じオブジェクトを指す別名
    b.add("B");
    IO.println("a = " + a + " / b = " + b + " / 同一? " + (a == b));

    // --- final は「再代入禁止」であって「不変」ではない ---
    final List<String> fixed = new ArrayList<>();
    fixed.add("中身は変えられる");
    IO.println("final の中身 = " + fixed);
    // fixed = new ArrayList<>();       // ← これはコンパイルエラー

    // --- var は「型推論」であって「動的型」ではない ---
    var votes = 3;                      // int に確定
    var names = new ArrayList<String>();// ArrayList<String> に確定
    names.add("alice");
    IO.println("votes は int、names は " + names.getClass().getSimpleName());
    // votes = "three";                 // ← コンパイルエラー
}
