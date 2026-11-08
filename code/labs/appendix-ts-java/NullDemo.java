import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record User(String name, String nickname) {}      // nickname は null があり得る

Optional<User> find(Map<String, User> db, String id) {
    return Optional.ofNullable(db.get(id));       // Map#get は無ければ null を返す
}

void main() {
    var db = Map.of("u1", new User("Aki", null));

    // TS: db["u1"]?.nickname ?? "(なし)"
    String nick = find(db, "u1")
            .map(User::nickname)                  // nickname が null なら map の結果も空になる
            .orElse("(なし)");
    IO.println(nick);

    // TS: const n = raw ?? "(なし)"
    String raw = null;
    String fallback = Objects.requireNonNullElse(raw, "(なし)");
    IO.println(fallback);

    // TS: raw!.length  （! は Java に無い。null なら実行時に NPE）
    try {
        IO.println(raw.length());
    } catch (NullPointerException e) {
        IO.println("NPE: " + e.getMessage());
    }

    // TS: if (raw == null) throw new Error("raw は必須")
    try {
        Objects.requireNonNull(raw, "raw は必須");
    } catch (NullPointerException e) {
        IO.println("requireNonNull: " + e.getMessage());
    }

    // TS: x?.y?.z に近い連鎖
    Optional<String> chained = find(db, "nobody").map(User::name).map(String::toUpperCase);
    IO.println(chained.isPresent() + " / " + chained.orElse("-"));
}
