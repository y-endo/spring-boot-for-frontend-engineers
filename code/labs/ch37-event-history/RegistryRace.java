import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.function.BiConsumer;

/**
 * 購読の「登録」と、最後の1人の「解除」が同時に起きたとき、登録した購読が名簿から消えるか。
 * QuickVote のシニアレビュー指摘4（PollEventHub.subscribe）を、単体で再現する。
 *
 *   java RegistryRace.java
 */
public class RegistryRace {

    static final Map<Long, Set<String>> registry = new ConcurrentHashMap<>();

    // 解除: 空になったらキーごと名簿から外す（PollEventHub.close と同じ）
    static void unsubscribe(long pollId, String who) {
        registry.computeIfPresent(pollId, (id, set) -> {
            set.remove(who);
            return set.isEmpty() ? null : set;
        });
    }

    // 登録(悪い): 取得・作成と追加が別々の操作
    static void subscribeBad(long pollId, String who) {
        registry.computeIfAbsent(pollId, id -> ConcurrentHashMap.newKeySet()).add(who);
    }

    // 登録(良い): 取得・作成・追加を compute の中でひとまとめに。同じキーへの操作は直列になる
    static void subscribeGood(long pollId, String who) {
        registry.compute(pollId, (id, set) -> {
            Set<String> target = set != null ? set : ConcurrentHashMap.newKeySet();
            target.add(who);
            return target;
        });
    }

    static int run(int rounds, BiConsumer<Long, String> subscribe) throws Exception {
        int lost = 0;
        for (int i = 0; i < rounds; i++) {
            registry.clear();
            registry.computeIfAbsent(1L, id -> ConcurrentHashMap.newKeySet()).add("old");
            CyclicBarrier start = new CyclicBarrier(2);
            Thread closer = Thread.ofPlatform().start(() -> {
                await(start);
                unsubscribe(1L, "old");        // 最後の1人が切断する
            });
            Thread joiner = Thread.ofPlatform().start(() -> {
                await(start);
                subscribe.accept(1L, "new");   // ちょうど同時に、新しい人が接続する
            });
            closer.join();
            joiner.join();
            Set<String> now = registry.get(1L);
            if (now == null || !now.contains("new")) {
                lost++;                          // 登録したはずの購読が名簿にいない
            }
        }
        return lost;
    }

    static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        int rounds = 20_000;
        System.out.println("computeIfAbsent(...).add : 購読が消えた回数 = " + run(rounds, RegistryRace::subscribeBad) + " / " + rounds);
        System.out.println("compute でひとまとめ       : 購読が消えた回数 = " + run(rounds, RegistryRace::subscribeGood) + " / " + rounds);
    }
}
