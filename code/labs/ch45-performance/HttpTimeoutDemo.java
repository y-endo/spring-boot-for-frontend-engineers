import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

/**
 * 外部 API の呼び出しに、タイムアウトを決めないとどうなるか。
 * 応答を60秒返さない相手（/slow）を用意し、JDK の HttpClient で呼ぶ。
 *   (1) タイムアウトを何も決めない → 5秒待っても戻らない（観察を打ち切って終了する）
 *   (2) 接続 1秒・読み取り(リクエスト全体) 2秒 → 約2秒で HttpTimeoutException
 */
public class HttpTimeoutDemo {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
            }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");

        // (1) 何も決めない
        HttpClient plain = HttpClient.newHttpClient();
        long t0 = System.nanoTime();
        Thread caller = Thread.ofPlatform().daemon().start(() -> {
            try {
                plain.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
                System.out.println("(1) 戻ってきた");
            } catch (Exception e) {
                System.out.println("(1) 例外: " + e);
            }
        });
        caller.join(Duration.ofSeconds(5));
        System.out.printf("(1) タイムアウトなし: %.1f 秒たっても %s%n",
                (System.nanoTime() - t0) / 1e9, caller.isAlive() ? "戻らない（スレッドは待ち続けている）" : "戻った");

        // (2) 接続とリクエスト全体に期限を付ける
        HttpClient bounded = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long t1 = System.nanoTime();
        try {
            bounded.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.out.printf("(2) 期限あり: %.1f 秒で %s%n", (System.nanoTime() - t1) / 1e9, e.getClass().getName());
        }
        server.stop(0);
    }
}
