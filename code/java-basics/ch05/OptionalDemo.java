import java.util.List;
import java.util.Optional;

record Poll(String code, String title) {}

final List<Poll> polls = List.of(new Poll("AB12CD34", "ランチ"), new Poll("XY98ZW76", "飲み会の場所"));

Optional<Poll> findByCode(String code) {               // 「無いかもしれない」を戻り値の型で表す
    return polls.stream().filter(p -> p.code().equals(code)).findFirst();
}

String expensiveDefault() {
    IO.println("  (expensiveDefault が呼ばれた)");
    return "既定のタイトル";
}

void main() {
    IO.println("見つかる   : " + findByCode("AB12CD34").map(Poll::title).orElse("なし"));
    IO.println("見つからない: " + findByCode("NOTFOUND").map(Poll::title).orElse("なし"));

    IO.println("--- orElse は引数を必ず評価する ---");
    findByCode("AB12CD34").map(Poll::title).orElse(expensiveDefault());
    IO.println("--- orElseGet は必要なときだけ ---");
    findByCode("AB12CD34").map(Poll::title).orElseGet(this::expensiveDefault);

    IO.println("--- 見つからなければ例外にする ---");
    try {
        findByCode("NOTFOUND").orElseThrow(() -> new IllegalStateException("投票が見つかりません: NOTFOUND"));
    } catch (IllegalStateException e) {
        IO.println(e.getMessage());
    }

    IO.println("--- Optional.of(null) は即 NPE ---");
    try {
        Optional.of((String) null);
    } catch (NullPointerException e) {
        IO.println("NullPointerException");
    }
    IO.println("Optional.ofNullable(null) = " + Optional.ofNullable((String) null));
}
