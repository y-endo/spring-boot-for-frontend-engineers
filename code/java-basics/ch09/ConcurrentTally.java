import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

void vote(Map<String, Integer> tally, int threads, int perThread) throws InterruptedException {
    Thread[] ts = new Thread[threads];
    for (int i = 0; i < threads; i++) {
        ts[i] = Thread.ofPlatform().start(() -> {
            for (int k = 0; k < perThread; k++) {
                tally.merge("カレー", 1, Integer::sum);
            }
        });
    }
    for (Thread t : ts) t.join();
}

void main() throws Exception {
    int threads = 8, perThread = 50_000;
    IO.println("期待値 = " + threads * perThread);

    Map<String, Integer> plain = new HashMap<>();
    try {
        vote(plain, threads, perThread);
        IO.println("HashMap           = " + plain.get("カレー"));
    } catch (Exception e) {
        IO.println("HashMap           = 例外 " + e);
    }

    Map<String, Integer> safe = new ConcurrentHashMap<>();
    vote(safe, threads, perThread);
    IO.println("ConcurrentHashMap = " + safe.get("カレー"));
}
