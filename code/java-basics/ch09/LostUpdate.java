import java.util.concurrent.atomic.AtomicInteger;

int plain = 0;                                         // ただの int フィールド
int guarded = 0;
final Object lock = new Object();
final AtomicInteger atomic = new AtomicInteger();

void runInThreads(int threads, int perThread, Runnable body) throws InterruptedException {
    Thread[] ts = new Thread[threads];
    for (int i = 0; i < threads; i++) {
        ts[i] = new Thread(() -> {
            for (int k = 0; k < perThread; k++) body.run();
        });
        ts[i].start();
    }
    for (Thread t : ts) t.join();                      // 全員の終了を待つ
}

void main() throws InterruptedException {
    int threads = 100, perThread = 10_000;
    IO.println("期待値 = " + threads * perThread);

    runInThreads(threads, perThread, () -> plain++);
    IO.println("int の ++              = " + plain);

    runInThreads(threads, perThread, () -> {
        synchronized (lock) { guarded++; }
    });
    IO.println("synchronized で守った  = " + guarded);

    runInThreads(threads, perThread, atomic::incrementAndGet);
    IO.println("AtomicInteger          = " + atomic.get());
}
