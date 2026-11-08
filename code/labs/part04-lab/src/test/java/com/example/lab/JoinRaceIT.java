package com.example.lab;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

import com.example.lab.ch25.JoinServices;
import com.example.lab.ch25.PollFullException;
import com.example.lab.ch25.RaceWindow;
import com.example.lab.support.AbstractLabIT;
import com.example.lab.support.Concurrently;
import com.example.lab.support.Concurrently.Outcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第25章: 「残り1枠に20人」を、運に頼らず狙って起こす。
 * RaceWindow（数えた後・挿入する前の隙間）に CyclicBarrier を挟み、「全員が数え終わるまで、誰も挿入しない」状況を作る。
 */
class JoinRaceIT extends AbstractLabIT {

    @Autowired
    RaceWindow window;
    @Autowired
    JoinServices.Unsafe unsafe;
    @Autowired
    JoinServices.TransactionOnly transactionOnly;
    @Autowired
    JoinServices.Locked locked;
    @Autowired
    JoinServices.InviteAcceptWithoutLock inviteWithoutLock;

    @AfterEach
    void clearHook() {
        window.clearHook();
    }

    /** 全員が「数え終わった」地点に揃うまで待つ。これで「全員が49人と見た」状態が必ずできる。 */
    private void waitUntilAllHaveCounted(int parties) {
        CyclicBarrier barrier = new CyclicBarrier(parties);
        window.setHook(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void ロックなし_残り1枠に20人が同時に参加すると_上限を超える() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(20);
        waitUntilAllHaveCounted(20);

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> {
            unsafe.join(pollId, visitors.get(i));
            return null;
        });

        assertThat(outcomes).allMatch(Outcome::succeeded);               // 20人全員が「参加できた」
        assertThat(repo.countParticipants(pollId)).isEqualTo(69);        // 上限50のはずが69人
    }

    @Test
    void トランザクションを付けただけでも_上限を超える() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(20);
        waitUntilAllHaveCounted(20);

        Concurrently.run(20, i -> () -> {
            transactionOnly.join(pollId, visitors.get(i));
            return null;
        });

        assertThat(repo.countParticipants(pollId)).isEqualTo(69);
    }

    @Test
    void 親行をロックすれば_同じ隙間があっても成功はちょうど1人() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(20);
        window.setHook(() -> sleep(100));                                // 隙間は広いまま。ロックが後続を待たせる

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> {
            locked.join(pollId, visitors.get(i));
            return null;
        });

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof PollFullException).hasSize(19);
        assertThat(repo.countParticipants(pollId)).isEqualTo(50);
    }

    @Test
    void 片方の経路がロックを忘れると_もう片方が正しくても上限を超える() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(2);
        window.setHook(() -> sleep(300));

        // 参加（ロックあり）と招待の受諾（ロックなし）が同時に来る
        List<Outcome<Object>> outcomes = Concurrently.run(2, i -> () -> {
            if (i == 0) {
                locked.join(pollId, visitors.get(0));
            } else {
                inviteWithoutLock.accept(pollId, visitors.get(1));
            }
            return null;
        });

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(repo.countParticipants(pollId)).isEqualTo(51);
    }

    @Test
    void すべての経路が同じ規則に従えば守られる() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(2);
        window.setHook(() -> sleep(300));

        List<Outcome<Object>> outcomes = Concurrently.run(2, i -> () -> {
            locked.join(pollId, visitors.get(i));                        // 招待の受諾も、同じロックを使う形に直した
            return null;
        });

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(repo.countParticipants(pollId)).isEqualTo(50);
    }

    /**
     * 「100回動かして失敗しなかった」の正体。隙間を作らずに、残り1枠を2人で取り合う。
     * ① 1人ずつ順番に参加させるテスト（普通の単体テスト）は、何回やっても壊れない。
     * ② 2人がほぼ同時に来る（到着のずれが 0〜20ms のランダム）と、壊れる回数は環境と運で変わる。
     * ③ 20人が完全に同時に来ると、ほぼ毎回壊れる。
     */
    @Test
    void 隙間を作らずに繰り返す_壊れた回数を数える() {
        int rounds = 100;

        int brokenSequential = 0;
        for (int round = 0; round < rounds; round++) {
            long pollId = pollWithMembers(49);
            List<Long> visitors = newVisitors(2);
            for (long visitor : visitors) {
                try {
                    unsafe.join(pollId, visitor);
                } catch (PollFullException ignored) {
                    // 2人目は満員で断られる。これが正しい動き
                }
            }
            if (repo.countParticipants(pollId) > 50) {
                brokenSequential++;
            }
        }

        int brokenSkewed = 0;
        for (int round = 0; round < rounds; round++) {
            long pollId = pollWithMembers(49);
            List<Long> visitors = newVisitors(2);
            Concurrently.run(2, i -> () -> {
                sleep(i == 0 ? 0 : ThreadLocalRandom.current().nextLong(20));
                try {
                    unsafe.join(pollId, visitors.get(i));
                } catch (PollFullException ignored) {
                    // 満員で断られるのは正しい動き
                }
                return null;
            });
            if (repo.countParticipants(pollId) > 50) {
                brokenSkewed++;
            }
        }
        int brokenAtOnce = 0;
        for (int round = 0; round < rounds; round++) {
            long pollId = pollWithMembers(49);
            List<Long> visitors = newVisitors(20);
            Concurrently.run(20, i -> () -> {
                try {
                    unsafe.join(pollId, visitors.get(i));
                } catch (PollFullException ignored) {
                    // 満員で断られるのは正しい動き
                }
                return null;
            });
            if (repo.countParticipants(pollId) > 50) {
                brokenAtOnce++;
            }
        }
        System.out.println("LUCK: 順番に実行 " + brokenSequential + "/" + rounds + " 回が壊れた。2人がほぼ同時(0〜20msずれ) "
                + brokenSkewed + "/" + rounds + " 回が壊れた。20人が完全に同時 " + brokenAtOnce + "/" + rounds + " 回が壊れた");
        assertThat(brokenSequential).isZero();                            // 順番に実行する限り、バグは絶対に見つからない
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
