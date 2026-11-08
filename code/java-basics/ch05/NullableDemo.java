import org.jspecify.annotations.Nullable;

record Poll(String title, @Nullable String description) {}

String shout(String s) {
    return s.toUpperCase();
}

void main() {
    var p = new Poll("ランチ", null);
    IO.println(p.description() == null ? "(説明なし)" : shout(p.description()));
}
