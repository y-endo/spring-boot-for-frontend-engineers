import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

String fetchUser(int id) {
    sleep(200);                       // ブロッキング呼び出し（HTTP/DB の待ち時間の代わり）
    return "user" + id;
}

void sleep(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
}

void main() throws Exception {
    // --- await に当たるもの: 普通のメソッド呼び出し（スレッドがその場で待つ）
    long t0 = System.nanoTime();
    String u = fetchUser(1);
    IO.println(u + " (" + (System.nanoTime() - t0) / 100_000_000 * 100 + "ms 前後)");

    // --- Promise.all([...]) 相当: 仮想スレッドで並列に待つ
    t0 = System.nanoTime();
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<String>> fs = List.of(
                ex.submit(() -> fetchUser(1)),
                ex.submit(() -> fetchUser(2)),
                ex.submit(() -> fetchUser(3)));
        for (Future<String> f : fs) IO.print(f.get() + " ");     // f.get() が await に近い
    }
    IO.println("(3件を並列: " + (System.nanoTime() - t0) / 100_000_000 * 100 + "ms 前後)");

    // --- Promise 相当: CompletableFuture
    CompletableFuture<String> cf = CompletableFuture
            .supplyAsync(() -> fetchUser(7))                     // new Promise(...) / async 関数
            .thenApply(String::toUpperCase)                      // .then(x => x.toUpperCase())
            .exceptionally(e -> "failed");                       // .catch(...)
    IO.println(cf.get());

    // --- Promise.all(...)
    var a = CompletableFuture.supplyAsync(() -> fetchUser(1));
    var b = CompletableFuture.supplyAsync(() -> fetchUser(2));
    CompletableFuture.allOf(a, b).join();
    IO.println(a.join() + "," + b.join());

    // --- 失敗した Promise / try-catch の対応
    var bad = CompletableFuture.<String>failedFuture(new IllegalStateException("boom"));
    try {
        bad.join();
    } catch (CompletionException e) {
        IO.println("cause = " + e.getCause());                   // 元の例外は getCause() に包まれる
    }

    // --- setTimeout 相当
    var sch = Executors.newScheduledThreadPool(1);
    sch.schedule(() -> IO.println("timer fired"), 100, TimeUnit.MILLISECONDS).get();
    sch.shutdown();

    // --- Promise.race 相当
    var slow = CompletableFuture.supplyAsync(() -> { sleep(300); return "slow"; });
    var fast = CompletableFuture.supplyAsync(() -> { sleep(50); return "fast"; });
    IO.println(CompletableFuture.anyOf(slow, fast).get());
}
