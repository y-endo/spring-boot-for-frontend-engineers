package com.example.quickvote.di;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第11章の事故: Bean（singleton）のフィールドに、リクエスト固有の値（誰が操作しているか）を置くと、他人の値と混ざる。
 * 第9章の SharedField と同じ事故を、Spring のコンテナの上で再現する。
 */
class SingletonStateAccidentTest {

    /** DB の応答待ちの代わり。2つのリクエストが「同時にここまで来た」ことを保証する（待ち合わせ）。 */
    interface DbWait {
        void await();
    }

    /** 悪い例: 「今の訪問者」をフィールドに持つ。Bean は1つしか作られないので、全リクエストで共有される。 */
    static class BadGreetingService {
        private final DbWait db;
        private String currentVoter; // ← 全リクエストで共有されるフィールド

        BadGreetingService(DbWait db) {
            this.db = db;
        }

        String greet(String voter) {
            this.currentVoter = voter;   // (1) 自分の名前を書く
            db.await();                  // (2) DB を待つ間に、別のリクエストが書き換える
            return currentVoter + " さん、投票を受け付けました"; // (3) 読み戻すと他人の名前
        }
    }

    /** 良い例: リクエスト固有の値は、ローカル変数とメソッドの引数だけで持ち回る。フィールドは依存する部品（不変）だけ。 */
    static class GoodGreetingService {
        private final DbWait db;

        GoodGreetingService(DbWait db) {
            this.db = db;
        }

        String greet(String voter) {
            db.await();
            return voter + " さん、投票を受け付けました";
        }
    }

    private static DbWait twoPartyBarrier() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        return () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DbWait.class, SingletonStateAccidentTest::twoPartyBarrier);

    @Test
    void Beanは1つしか作られず_getBeanは何度呼んでも同じインスタンス() {
        runner.withBean(BadGreetingService.class).run(ctx ->
                assertThat(ctx.getBean(BadGreetingService.class)).isSameAs(ctx.getBean(BadGreetingService.class)));
    }

    @Test
    void フィールドに訪問者を置くと_aliceの画面にbobさんと出る() {
        runner.withBean(BadGreetingService.class).run(ctx -> {
            BadGreetingService service = ctx.getBean(BadGreetingService.class);
            String[] results = runConcurrently(service::greet);

            System.out.println("[demo] フィールド版 alice -> " + results[0]);
            System.out.println("[demo] フィールド版 bob   -> " + results[1]);
            // 最後に書いた人の名前が、両方のリクエストに返る。少なくとも片方は他人の名前
            assertThat(results[0]).isEqualTo(results[1]);
        });
    }

    @Test
    void ローカル変数だけで持ち回れば_混ざらない() {
        runner.withBean(GoodGreetingService.class).run(ctx -> {
            GoodGreetingService service = ctx.getBean(GoodGreetingService.class);
            String[] results = runConcurrently(service::greet);

            System.out.println("[demo] ローカル変数版 alice -> " + results[0]);
            System.out.println("[demo] ローカル変数版 bob   -> " + results[1]);
            assertThat(results[0]).startsWith("alice");
            assertThat(results[1]).startsWith("bob");
        });
    }

    /** alice と bob のリクエストを別スレッドで同時に実行し、それぞれの応答を返す。 */
    private static String[] runConcurrently(java.util.function.Function<String, String> handler) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> alice = pool.submit(() -> handler.apply("alice"));
            Future<String> bob = pool.submit(() -> handler.apply("bob"));
            return new String[] { alice.get(10, TimeUnit.SECONDS), bob.get(10, TimeUnit.SECONDS) };
        }
    }
}
