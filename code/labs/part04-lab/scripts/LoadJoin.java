import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 負荷スクリプト: 参加者49人の投票に、20人が同時に参加する。
 * 使い方: java -Dstdout.encoding=UTF-8 scripts/LoadJoin.java http://localhost:18025 unsafe|tx-only|locked [同時人数]
 *
 * 全員を CountDownLatch で揃えて一斉に送る。結果は「HTTPステータスごとの件数」と「DBの参加者数」。
 */
void main(String[] args) throws Exception {
    String base = args[0];
    String mode = args[1];
    int candidates = args.length > 2 ? Integer.parseInt(args[2]) : 20;
    HttpClient client = HttpClient.newHttpClient();

    String seedJson = client.send(
            HttpRequest.newBuilder(URI.create(base + "/lab/ch25/seed?members=49&candidates=" + candidates))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()).body();
    Matcher m = Pattern.compile("\"pollId\":(\\d+)").matcher(seedJson);
    m.find();
    long pollId = Long.parseLong(m.group(1));
    List<Long> visitorIds = new ArrayList<>();
    Matcher ids = Pattern.compile("\\[(.*?)\\]").matcher(seedJson);
    ids.find();
    for (String id : ids.group(1).split(",")) {
        visitorIds.add(Long.parseLong(id.trim()));
    }
    System.out.println("投票 " + pollId + ": 参加者49人 / 上限50人 / これから同時に参加する人 " + visitorIds.size() + " 人 / 方式 = " + mode);

    CountDownLatch ready = new CountDownLatch(visitorIds.size());
    CountDownLatch go = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(visitorIds.size());
    List<Future<Integer>> futures = new ArrayList<>();
    for (long visitorId : visitorIds) {
        futures.add(executor.submit(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    base + "/lab/ch25/join/" + mode + "?pollId=" + pollId + "&visitorId=" + visitorId))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
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

    String count = client.send(
            HttpRequest.newBuilder(URI.create(base + "/lab/ch25/count?pollId=" + pollId)).build(),
            HttpResponse.BodyHandlers.ofString()).body();
    System.out.println("HTTPステータスごとの件数: " + byStatus + "   (200=参加できた, 409=満員で断られた)");
    System.out.println("DBの参加者数: " + count + "   (上限は50)");
}
