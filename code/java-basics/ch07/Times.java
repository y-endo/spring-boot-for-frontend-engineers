import java.time.*;
import java.time.format.DateTimeFormatter;

void main() {
    ZoneId sys = ZoneId.systemDefault();
    IO.println("このJVMのタイムゾーン = " + sys);

    // 主催者が画面に入力した「11月8日 23:30 締切」を LocalDateTime として受け取った
    LocalDateTime input = LocalDateTime.of(2026, 11, 8, 23, 30);
    IO.println("LocalDateTime        = " + input + "   （これだけでは、いつなのか決まらない）");

    // 悪い例：サーバーのタイムゾーン任せで Instant にする
    Instant closesAt = input.atZone(sys).toInstant();
    IO.println("サーバー任せの Instant = " + closesAt);

    // 良い例：入力者のタイムゾーンを明示する
    Instant explicit = input.atZone(ZoneId.of("Asia/Tokyo")).toInstant();
    IO.println("Asia/Tokyo 明示       = " + explicit);

    // 現在時刻との比較
    Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);   // 日本時間 11月9日 0:00
    Instant now = Instant.now(clock);
    IO.println("now                  = " + now);
    IO.println("サーバー任せ版: 締切前? " + now.isBefore(closesAt));
    IO.println("Asia/Tokyo明示版: 締切前? " + now.isBefore(explicit));

    // 同じ瞬間を、別の地域の壁時計で見る
    ZonedDateTime tokyo = explicit.atZone(ZoneId.of("Asia/Tokyo"));
    ZonedDateTime ny    = explicit.atZone(ZoneId.of("America/New_York"));
    IO.println("同じ瞬間: " + tokyo + " / " + ny);
    IO.println("ISO 8601 (JSON向け)   = " + explicit + "  ← Instant.toString() は常に UTC");
    IO.println("表示用に整形          = " + DateTimeFormatter.ofPattern("M月d日 H:mm").format(tokyo));

    // おまけ：夏時間（サマータイム）の地域では「存在しない時刻」や「23時間の日」がある
    var ny0 = ZoneId.of("America/New_York");
    var gap = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 2, 30), ny0);
    IO.println("2026-03-08 02:30 (New York) = " + gap + "  ← 実在しない時刻は後ろにずれる");
    var dayStart = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny0);
    IO.println("2026-03-08 の長さ = " + Duration.between(dayStart, dayStart.plusDays(1)).toHours() + " 時間");
}
