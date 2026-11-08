import java.util.HashMap;
import java.util.Map;

record Owner(String name) {}
record Poll(String title, Owner owner) {}

void main() {
    Map<String, Poll> store = new HashMap<>();
    store.put("AB12CD34", new Poll("ランチ", null));      // owner が null の投票

    // (1) 存在しないキー → get は null を返す
    try {
        Poll p = store.get("ZZZZZZZZ");
        IO.println(p.title());
    } catch (NullPointerException e) {
        IO.println("(1) " + e.getMessage());
    }

    // (2) 途中の値が null
    try {
        Poll p = store.get("AB12CD34");
        IO.println(p.owner().name().length());
    } catch (NullPointerException e) {
        IO.println("(2) " + e.getMessage());
    }

    // (3) Map のキーに null を入れる/読む（実装によって挙動が違う）
    try {
        Map.of("a", 1).get(null);
    } catch (NullPointerException e) {
        IO.println("(3) Map.of(...).get(null) は NPE");
    }
    IO.println("(3) HashMap.get(null) = " + store.get(null));
}
