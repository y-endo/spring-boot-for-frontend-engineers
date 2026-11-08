import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

void main() throws Exception {
    int n = 10_000;
    var finished = new AtomicInteger();
    long start = System.nanoTime();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {   // try-with-resources：全タスク完了まで待つ
        for (int i = 0; i < n; i++) {
            executor.submit(() -> {
                Thread.sleep(Duration.ofSeconds(1));       // 1 秒ブロック
                finished.incrementAndGet();
                return null;
            });
        }
    }
    long ms = (System.nanoTime() - start) / 1_000_000;
    IO.println(n + " 個の仮想スレッドが各 1 秒ブロック → 全体で " + ms + " ms / 完了 " + finished.get());

    Thread t = Thread.ofVirtual().unstarted(() -> {});
    IO.println("仮想スレッドか? " + t.isVirtual() + " / 守護スレッドか? " + t.isDaemon());
}
