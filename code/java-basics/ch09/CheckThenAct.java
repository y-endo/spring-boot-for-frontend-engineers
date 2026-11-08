import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** 参加者は最大 50 人。「数えてから足す」は、同時に来ると上限を超える。 */
class Room {
    static final int MAX = 50;
    private int members = 0;
    private final AtomicInteger safeMembers = new AtomicInteger();

    boolean joinUnsafe() {
        if (members < MAX) {                           // (1) 空きがあるか確認する
            pause();                                   // (2) DB への問い合わせなどで、少し待つ
            members++;                                 // (3) 参加を記録する
            return true;
        }
        return false;
    }

    private static void pause() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    boolean joinSafe() {
        while (true) {                                 // 比較して入れ替える（CAS）を、成功するまで繰り返す
            int current = safeMembers.get();
            if (current >= MAX) return false;
            if (safeMembers.compareAndSet(current, current + 1)) return true;
        }
    }

    int unsafeCount() { return members; }
    int safeCount() { return safeMembers.get(); }
}

void main() throws InterruptedException {
    int applicants = 200;
    Room room = new Room();
    CountDownLatch start = new CountDownLatch(1);      // 「よーいドン」で全員が同時に動き出すための合図
    CountDownLatch done = new CountDownLatch(applicants);
    AtomicInteger unsafeOk = new AtomicInteger(), safeOk = new AtomicInteger();

    for (int i = 0; i < applicants; i++) {
        Thread.ofPlatform().start(() -> {
            try {
                start.await();
                if (room.joinUnsafe()) unsafeOk.incrementAndGet();
                if (room.joinSafe()) safeOk.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
    }
    start.countDown();
    done.await();

    IO.println("上限 " + Room.MAX + " 人 / 申込 " + applicants + " 人");
    IO.println("数えてから足す（unsafe）: 参加 " + unsafeOk.get() + " 人, 記録上の人数 " + room.unsafeCount());
    IO.println("compareAndSet（safe）    : 参加 " + safeOk.get() + " 人, 記録上の人数 " + room.safeCount());
}
