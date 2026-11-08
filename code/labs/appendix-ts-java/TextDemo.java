import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

void main() {
    String name = "QuickVote";
    int n = 3;
    // `Hello ${name}, ${n} votes`
    IO.println("Hello " + name + ", " + n + " votes");
    IO.println("Hello %s, %d votes".formatted(name, n));

    // 複数行テンプレートリテラル → テキストブロック
    String json = """
            {
              "title": "ランチ",
              "votes": %d
            }
            """.formatted(n);
    IO.print(json);

    // 文字列メソッド
    IO.println("  a,b ,c ".trim().split("\\s*,\\s*").length + " / " + "abc".repeat(2) + " / " + "Abc".toLowerCase()
            + " / " + "abc".contains("b") + " / " + "abc".substring(1) + " / " + "a-b".replace("-", "+"));

    // 等価: === ではなく equals
    String a = new String("x"), b = new String("x");
    IO.println((a == b) + " / " + a.equals(b) + " / " + Objects.equals(null, null));

    // 日時: new Date() / Date.now() / toISOString()
    Instant fixed = Instant.parse("2026-11-08T03:04:05Z");
    IO.println(fixed + " / " + fixed.toEpochMilli());
    IO.println(fixed.plus(Duration.ofDays(7)));
    ZonedDateTime tokyo = fixed.atZone(ZoneId.of("Asia/Tokyo"));
    IO.println(tokyo.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) + " " + tokyo.getZone());
    IO.println(Duration.between(fixed, fixed.plusSeconds(90)).toSeconds() + "s");
    // Date.now() 相当の現在時刻（テストでは Clock を注入して固定する）
    Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);
    IO.println(Instant.now(clock));
}
