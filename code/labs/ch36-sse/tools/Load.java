import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE の接続を n 本つなぎ、waitSeconds 秒のあいだに「ヘッダーが届いた」接続が何本あるかを数える。
 *   java tools/Load.java http://localhost:18036/sse/blocking 300 5
 */
public class Load {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        int n = Integer.parseInt(args[1]);
        int waitSeconds = Integer.parseInt(args[2]);
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        AtomicInteger connected = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
                connected.incrementAndGet();               // ヘッダーが届いた
                response.body().forEach(line -> { });      // 読み続ける（切るまで）
            });
        }
        for (int s = 1; s <= waitSeconds; s++) {
            Thread.sleep(1000);
            System.out.printf("%d 秒後: %d / %d 本が接続できた%n", s, connected.get(), n);
        }
        System.exit(0);
    }
}
