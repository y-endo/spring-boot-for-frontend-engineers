import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 第28章：締切の判定に使う「現在時刻」を、ロックを取る前に読むか、取った後に読むか。
 * ロック待ちは SELECT ... FOR UPDATE の待ちの模型（ReentrantLock）。
 */
public class StaleNow {

    static final Clock CLOCK = Clock.systemUTC();
    static final ReentrantLock POLL_ROW = new ReentrantLock();   // polls の行ロックの代わり
    static Instant t0;
    static Instant closesAt;

    static long ms() { return Duration.between(t0, CLOCK.instant()).toMillis(); }

    /** 悪い例：時刻を先に読み、ロックを待ち、古い時刻で判定する。 */
    static String voteBad() {
        Instant now = CLOCK.instant();                 // (1)! ロックの前に時刻を読む
        POLL_ROW.lock();
        try {
            boolean open = now.isBefore(closesAt);
            return (open ? "受理" : "拒否") + "（判定に使った時刻 t=" + Duration.between(t0, now).toMillis()
                    + "ms / 実際に書き込む時刻 t=" + ms() + "ms）";
        } finally {
            POLL_ROW.unlock();
        }
    }

    /** 良い例：ロックを取ってから時刻を読む。 */
    static String voteGood() {
        POLL_ROW.lock();
        try {
            Instant now = CLOCK.instant();             // (2)! ロックを取った「後」に時刻を読む
            boolean open = now.isBefore(closesAt);
            return (open ? "受理" : "拒否") + "（判定に使った時刻 t=" + Duration.between(t0, now).toMillis()
                    + "ms / 実際に書き込む時刻 t=" + ms() + "ms）";
        } finally {
            POLL_ROW.unlock();
        }
    }

    static void run(String name, java.util.function.Supplier<String> vote) throws Exception {
        t0 = CLOCK.instant();
        closesAt = t0.plusMillis(1000);                // 締切は t=1000ms
        Thread holder = Thread.ofPlatform().start(() -> {   // 別の処理が行を1500msの間ロックしている
            POLL_ROW.lock();
            try { Thread.sleep(1500); } catch (InterruptedException e) { /* 実験なので無視 */ }
            finally { POLL_ROW.unlock(); }
        });
        Thread.sleep(100);                             // holder がロックを取るのを待つ
        Thread.sleep(200);                             // 投票リクエストが届くのは t=300ms（締切の前）
        IO.println(name + ": 締切は t=1000ms。リクエストは t=" + ms() + "ms に到着");
        IO.println("  → " + vote.get());
        holder.join();
    }

    void main() throws Exception {
        run("時刻を先に読む版", StaleNow::voteBad);
        run("ロック後に読む版", StaleNow::voteGood);
    }
}
