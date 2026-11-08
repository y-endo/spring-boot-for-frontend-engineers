import java.time.Instant;
import java.util.List;

record Option(String label, int votes) {
    Option {
        if (votes < 0) throw new IllegalArgumentException("票数は0以上です: " + votes);
    }

    Option(String label) {                        // 引数を省略できるように、コンストラクタを足す
        this(label, 0);
    }

    static Option of(String label) {              // 静的ファクトリ：名前で意図を表す
        return new Option(label);
    }

    Option withVotes(int newVotes) {              // 「変更」は、新しいオブジェクトを作ること
        return new Option(label, newVotes);
    }
}

record Range(int min, int max) {
    Range {
        if (min > max) throw new IllegalArgumentException("min > max: " + min + " > " + max);
    }
}

String describe(Object o) {
    if (o instanceof Option(String label, int votes) && votes > 0) {     // instanceof でも分解できる
        return label + " は " + votes + " 票";
    }
    return "その他";
}

void main() {
    Option a = Option.of("カレー");
    Option b = a.withVotes(3);
    IO.println(a + " / " + b);
    IO.println(describe(a) + " / " + describe(b) + " / " + describe("文字列"));

    try {
        a.withVotes(-1);
    } catch (IllegalArgumentException e) {
        IO.println("検証: " + e.getMessage());
    }
    try {
        new Range(5, 1);
    } catch (IllegalArgumentException e) {
        IO.println("検証: " + e.getMessage());
    }
}
