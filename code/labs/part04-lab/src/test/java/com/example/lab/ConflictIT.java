package com.example.lab;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.lab.ch25.PollFullException;
import com.example.lab.ch26.DeadlockDemo;
import com.example.lab.ch26.MemoService;
import com.example.lab.ch26.Retries;
import com.example.lab.ch26.SerializableJoinService;
import com.example.lab.support.AbstractLabIT;
import com.example.lab.support.Concurrently;
import com.example.lab.support.Concurrently.Outcome;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.PessimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 第26章: 競合の別解（楽観ロック・ON CONFLICT・SERIALIZABLE）、デッドロック、再試行。 */
class ConflictIT extends AbstractLabIT {

    @Autowired
    MemoService memos;
    @Autowired
    DeadlockDemo deadlock;
    @Autowired
    SerializableJoinService serializableJoin;

    // ---- 楽観ロック ----

    @Test
    void 後勝ちの更新は_先に保存した人の変更を黙って消す() {
        memos.create(1, "最初");
        var seenByA = memos.find(1);
        var seenByB = memos.find(1);

        memos.saveLastWriteWins(1, seenByA.body() + " + Aの追記");
        memos.saveLastWriteWins(1, seenByB.body() + " + Bの追記");

        assertThat(memos.find(1).body()).isEqualTo("最初 + Bの追記");     // Aの追記は消えた。エラーは出ない
    }

    @Test
    void 楽観ロックなら_後から保存した人が競合として分かる() {
        memos.create(1, "最初");
        var seenByA = memos.find(1);
        var seenByB = memos.find(1);

        memos.saveOptimistic(1, seenByA.body() + " + Aの追記", seenByA.version());
        assertThatThrownBy(() -> memos.saveOptimistic(1, seenByB.body() + " + Bの追記", seenByB.version()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(memos.find(1).body()).isEqualTo("最初 + Aの追記");     // Aの変更は守られた
        assertThat(memos.find(1).version()).isEqualTo(1);
    }

    @Test
    void 競合したら_読み直してやり直せば成功する() {
        memos.create(1, "最初");
        var seenByB = memos.find(1);
        memos.saveOptimistic(1, "Aの更新", memos.find(1).version());     // その間に A が更新した

        AtomicInteger attempts = new AtomicInteger();
        Retries.onConflict(3, () -> {
            attempts.incrementAndGet();
            var latest = attempts.get() == 1 ? seenByB : memos.find(1);    // 1回目は古い版、やり直しでは読み直す
            memos.saveOptimistic(1, latest.body() + " + Bの追記", latest.version());
            return null;
        });

        assertThat(attempts).hasValue(2);
        assertThat(memos.find(1).body()).isEqualTo("Aの更新 + Bの追記");
    }

    // ---- デッドロック ----

    @Test
    void 逆の順序でロックすると_デッドロックでどちらかが失敗する() {
        long pollA = pollWithMembers(1);
        long pollB = pollWithMembers(1);
        CountDownLatch bothHoldFirst = new CountDownLatch(2);

        List<Outcome<Object>> outcomes = Concurrently.run(2, i -> () -> {
            if (i == 0) {
                deadlock.lockInOrder(pollA, pollB, bothHoldFirst);
            } else {
                deadlock.lockInOrder(pollB, pollA, bothHoldFirst);
            }
            return null;
        });

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        Throwable failure = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error();
        System.out.println("DEADLOCK: " + failure.getClass().getName());
        assertThat(failure).isInstanceOf(PessimisticLockingFailureException.class);   // SQLSTATE 40P01 (deadlock detected)
    }

    @Test
    void 同じ順序でロックすれば_デッドロックしない() {
        long pollA = pollWithMembers(1);
        long pollB = pollWithMembers(1);
        // 同じ順序なら、2人目は1つ目のロックを取れずに待つだけ。「両方が1つ目を握る」状況はそもそも作れない。
        CountDownLatch bothHoldFirst = new CountDownLatch(1);

        List<Outcome<Object>> outcomes = Concurrently.run(2, i -> () -> {
            deadlock.lockSorted(i == 0 ? List.of(pollA, pollB) : List.of(pollB, pollA), bothHoldFirst);
            return null;
        });

        assertThat(outcomes).allMatch(Outcome::succeeded);
    }

    // ---- SERIALIZABLE ----

    @Test
    void SERIALIZABLEなら_ロックなしでも上限は守られるが_再試行が要る() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(20);
        AtomicInteger serializationFailures = new AtomicInteger();

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> Retries.onConflict(10, () -> {
            try {
                serializableJoin.join(pollId, visitors.get(i));
            } catch (PessimisticLockingFailureException e) {
                serializationFailures.incrementAndGet();
                throw e;
            }
            return null;
        }));

        System.out.println("SERIALIZABLE: 直列化の失敗(再試行された回数) = " + serializationFailures.get());
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof PollFullException).hasSize(19);
        assertThat(repo.countParticipants(pollId)).isEqualTo(50);
    }

    @Test
    void SERIALIZABLEでも再試行しなければ_直列化の失敗がそのまま利用者に届く() {
        long pollId = pollWithMembers(10);
        List<Long> visitors = newVisitors(20);

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> {
            serializableJoin.join(pollId, visitors.get(i));
            return null;
        });

        long failed = outcomes.stream().filter(o -> !o.succeeded()).count();
        System.out.println("SERIALIZABLE(再試行なし): 20人中 " + failed + " 人が失敗。例外 = "
                + outcomes.stream().filter(o -> !o.succeeded()).map(o -> o.error().getClass().getSimpleName())
                        .distinct().toList());
        assertThat(repo.countParticipants(pollId)).isLessThanOrEqualTo(50);
    }

    @Test
    void Spring7標準のRetryTemplateでも_同じ結果になる() {
        long pollId = pollWithMembers(49);
        List<Long> visitors = newVisitors(20);
        // 再試行するのは PessimisticLockingFailureException（直列化失敗・デッドロック）だけ。回数に上限があり、待ち時間にばらつきを足す。
        RetryTemplate retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(10)
                .delay(Duration.ofMillis(20))
                .jitter(Duration.ofMillis(20))
                .includes(PessimisticLockingFailureException.class)
                .build());

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> retry.invoke(() -> {
            serializableJoin.join(pollId, visitors.get(i));
            return null;
        }));

        System.out.println("RETRYTEMPLATE: 失敗の型 = " + outcomes.stream().filter(o -> !o.succeeded())
                .map(o -> o.error().getClass().getSimpleName()).distinct().toList());
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(repo.countParticipants(pollId)).isEqualTo(50);
    }
}
