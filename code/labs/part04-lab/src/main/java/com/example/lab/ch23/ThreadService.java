package com.example.lab.ch23;

import java.util.concurrent.CompletableFuture;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** トランザクションは「スレッドに紐づく」。別のスレッドで動かした処理は、このトランザクションに入らない。 */
@Service
public class ThreadService {

    private final LabRepository repo;

    public ThreadService(LabRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void insertInAnotherThread(String joinCodeMain, String joinCodeOther) {
        repo.insertPoll("このスレッド", joinCodeMain);
        System.out.println("  元のスレッド  : " + Thread.currentThread().getName() + " pid=" + repo.backendPid()
                + " " + TxProbe.state());
        CompletableFuture.runAsync(() -> {
            repo.insertPoll("別のスレッド", joinCodeOther);
            System.out.println("  別のスレッド  : " + Thread.currentThread().getName() + " pid=" + repo.backendPid()
                    + " " + TxProbe.state());
        }).join();
        throw new IllegalStateException("元のスレッドが失敗 → ロールバック");
    }
}
