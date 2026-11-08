import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class JoinCode {                                          // equals を書いていない普通のクラス
    final String value;
    JoinCode(String value) { this.value = value; }
}

class JoinCodeEq {                                        // equals だけ書いた（hashCode なし）
    final String value;
    JoinCodeEq(String value) { this.value = value; }
    @Override public boolean equals(Object o) {
        return o instanceof JoinCodeEq other && value.equals(other.value);
    }
}

class JoinCodeOk {                                        // equals と hashCode をセットで
    final String value;
    JoinCodeOk(String value) { this.value = value; }
    @Override public boolean equals(Object o) {
        return o instanceof JoinCodeOk other && value.equals(other.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}

record JoinCodeRec(String value) {}                       // record なら自動

void main() {
    // --- String ---
    String a = "AB12CD34";
    String b = "AB12CD34";
    String c = new String("AB12CD34");
    String d = String.join("", "AB12", "CD34");
    IO.println("a == b      : " + (a == b)      + "   （リテラル同士はたまたま同じ参照）");
    IO.println("a == c      : " + (a == c)      + "  （new は別オブジェクト）");
    IO.println("a == d      : " + (a == d)      + "  （実行時に組み立てた文字列も別オブジェクト）");
    IO.println("a.equals(c) : " + a.equals(c));

    // --- Integer キャッシュ ---
    Integer i1 = 127, i2 = 127, j1 = 128, j2 = 128;
    IO.println("127 == 127 : " + (i1 == i2));
    IO.println("128 == 128 : " + (j1 == j2) + "  （-128から127 だけキャッシュされる）");
    IO.println("128 equals : " + j1.equals(j2));

    // --- equals/hashCode と HashSet ---
    Set<Object> s1 = new HashSet<>();
    s1.add(new JoinCode("AB12CD34"));
    s1.add(new JoinCode("AB12CD34"));
    IO.println("equals なし            → Set の大きさ " + s1.size());

    Set<Object> s2 = new HashSet<>();
    s2.add(new JoinCodeEq("AB12CD34"));
    s2.add(new JoinCodeEq("AB12CD34"));
    IO.println("equals のみ            → Set の大きさ " + s2.size() + "  （hashCode が違うので別の棚に入る）");

    Set<Object> s3 = new HashSet<>();
    s3.add(new JoinCodeOk("AB12CD34"));
    s3.add(new JoinCodeOk("AB12CD34"));
    IO.println("equals + hashCode      → Set の大きさ " + s3.size());

    Set<Object> s4 = new HashSet<>();
    s4.add(new JoinCodeRec("AB12CD34"));
    s4.add(new JoinCodeRec("AB12CD34"));
    IO.println("record                 → Set の大きさ " + s4.size());
}
