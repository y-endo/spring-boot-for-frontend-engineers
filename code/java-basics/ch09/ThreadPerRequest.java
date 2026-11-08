import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

static final int REQUESTS = 8;
static final int BLOCK_MILLIS = 300;               // DB 問い合わせの待ち時間の代わり

void measure(String label, ExecutorService executor) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/slow", exchange -> {
        String who = Thread.currentThread().toString();
        try {
            Thread.sleep(BLOCK_MILLIS);            // ブロッキング：この間、このスレッドは何もできない
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = who.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    });
    server.setExecutor(executor);                  // リクエストを処理するスレッドの供給元
    server.start();

    var client = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")).build();

    long start = System.nanoTime();
    List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, REQUESTS)
            .mapToObj(_ -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
            .toList();
    var threads = new TreeSet<String>();
    for (var f : futures) {
        threads.add(f.get().body());
    }
    long elapsed = (System.nanoTime() - start) / 1_000_000;

    IO.println(label + " : " + REQUESTS + " 件の処理に約 " + elapsed + " ms / 使われたスレッド " + threads.size() + " 個");
    threads.stream().limit(3).forEach(t -> IO.println("    " + t));
    server.stop(0);
    executor.shutdown();
}

void main() throws Exception {
    IO.println("8 件を同時に投げる。各リクエストは 300ms ブロックする。");
    measure("スレッド 1 本", Executors.newSingleThreadExecutor());
    measure("スレッド 4 本（プール）", Executors.newFixedThreadPool(4));
    measure("仮想スレッド", Executors.newVirtualThreadPerTaskExecutor());
}
