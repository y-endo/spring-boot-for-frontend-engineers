import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * リファレンス実装（code/quickvote）の本物のAPIに対する負荷スクリプト。
 * 主催者が投票を作る → 48人が順番に参加(主催者と合わせて49人) → 残り1枠に、新しい20人が同時に POST /api/join する。
 *
 * 使い方: java -Dstdout.encoding=UTF-8 scripts/LoadJoinQuickVote.java http://localhost:18025 [同時人数]
 *
 * 訪問者のCookie(QV_VISITOR)は、入口のAPI(POST /api/join)が新しい訪問者に発行する。
 * 更新系のAPIには CSRF 対策が掛かっているので、最初の GET で XSRF-TOKEN のCookieをもらい、同じ値をヘッダーにも付ける。
 */
final HttpClient client = HttpClient.newHttpClient();

String cookieValue(HttpResponse<?> response, String name) {
    for (String setCookie : response.headers().allValues("set-cookie")) {
        if (setCookie.startsWith(name + "=")) {
            return setCookie.substring(name.length() + 1, setCookie.indexOf(';'));
        }
    }
    throw new IllegalStateException(name + " の Set-Cookie がない");
}

String newXsrf(String base) throws Exception {
    return cookieValue(client.send(HttpRequest.newBuilder(URI.create(base + "/")).build(),
            HttpResponse.BodyHandlers.discarding()), "XSRF-TOKEN");
}

HttpRequest post(String base, String path, String xsrf, String json) {
    return HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=" + xsrf)
            .header("X-XSRF-TOKEN", xsrf)
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
}

void main(String[] args) throws Exception {
    String base = args[0];
    int concurrent = args.length > 1 ? Integer.parseInt(args[1]) : 20;

    // 主催者が投票を作る
    String xsrf = newXsrf(base);
    HttpResponse<String> created = client.send(post(base, "/api/polls", xsrf,
            "{\"title\":\"負荷実験\",\"closesInMinutes\":30,\"allowMemberOptions\":false,\"options\":[\"カレー\",\"そば\"]}"),
            HttpResponse.BodyHandlers.ofString());
    if (created.statusCode() != 201) {
        throw new IllegalStateException("作成に失敗: " + created.statusCode() + " " + created.body());
    }
    String hostCookie = cookieValue(created, "QV_VISITOR");
    Matcher id = Pattern.compile("\"id\":\"([0-9a-f-]+)\"").matcher(created.body());
    id.find();
    String pollId = id.group(1);
    Matcher code = Pattern.compile("\"joinCode\":\"([A-Z0-9]+)\"").matcher(created.body());
    code.find();
    String joinCode = code.group(1);

    // 48人が順番に参加 → 主催者を含めて49人（残り1枠）
    for (int i = 0; i < 48; i++) {
        String x = newXsrf(base);
        int status = client.send(post(base, "/api/join", x, "{\"code\":\"" + joinCode + "\"}"),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status != 200) {
            throw new IllegalStateException("準備の参加に失敗: " + status);
        }
    }
    System.out.println("準備完了: 参加者 " + participantCount(base, pollId, xsrf, hostCookie) + " 人 / 上限50人 / これから同時に参加する人 "
            + concurrent + " 人");

    // 同時に参加する人たち。XSRFのCookieは事前に取っておき、送信だけを一斉に行う
    CountDownLatch ready = new CountDownLatch(concurrent);
    CountDownLatch go = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(concurrent);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < concurrent; i++) {
        String x = newXsrf(base);
        HttpRequest request = post(base, "/api/join", x, "{\"code\":\"" + joinCode + "\"}");
        futures.add(executor.submit(() -> {
            ready.countDown();
            go.await();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }));
    }
    ready.await();
    go.countDown();
    Map<Integer, Integer> byStatus = new TreeMap<>();
    for (Future<Integer> f : futures) {
        byStatus.merge(f.get(), 1, Integer::sum);
    }
    executor.shutdown();
    System.out.println("HTTPステータスごとの件数: " + byStatus + "   (200=参加できた, 409=満員 POLL_FULL)");
    System.out.println("参加者数(主催者が GET /api/polls/{id} で確認): " + participantCount(base, pollId, xsrf, hostCookie) + " 人");
}

int participantCount(String base, String pollId, String xsrf, String hostCookie) throws Exception {
    HttpResponse<String> detail = client.send(HttpRequest.newBuilder(URI.create(base + "/api/polls/" + pollId))
            .header("Cookie", "QV_VISITOR=" + hostCookie).build(), HttpResponse.BodyHandlers.ofString());
    Matcher m = Pattern.compile("\"participantCount\":(\\d+)").matcher(detail.body());
    if (!m.find()) {
        throw new IllegalStateException("詳細の取得に失敗: " + detail.statusCode() + " " + detail.body());
    }
    return Integer.parseInt(m.group(1));
}
