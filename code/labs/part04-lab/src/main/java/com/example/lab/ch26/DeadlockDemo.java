package com.example.lab.ch26;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * デッドロックの実験。2つの投票(poll)の行を、2つのトランザクションが逆の順序でロックする。
 * 例: 「投票Aと投票Bをまとめて締める」処理が、呼ぶ側によって A→B と B→A の順になる。
 */
@Service
public class DeadlockDemo {

    private final LabRepository repo;
    private final TransactionTemplate transaction;

    public DeadlockDemo(LabRepository repo, TransactionTemplate transaction) {
        this.repo = repo;
        this.transaction = transaction;
    }

    /**
     * first を先に、second を後にロックする。1つ目のロックを取った後、相手も1つ目を取るまで待つ
     * （両方が1つ目を握った状態を必ず作る = 競合を狙って起こす）。
     */
    public void lockInOrder(long first, long second, CountDownLatch bothHoldFirst) {
        transaction.executeWithoutResult(status -> {
            repo.lockPoll(first).orElseThrow();
            bothHoldFirst.countDown();
            try {
                if (!bothHoldFirst.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("相手が1つ目のロックを取れなかった");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            repo.lockPoll(second).orElseThrow();
        });
    }

    /** 直し方: どの呼び出し元も、id の小さい順にロックする。 */
    public void lockSorted(List<Long> pollIds, CountDownLatch bothHoldFirst) {
        List<Long> ordered = pollIds.stream().sorted().toList();
        lockInOrder(ordered.get(0), ordered.get(1), bothHoldFirst);
    }
}
