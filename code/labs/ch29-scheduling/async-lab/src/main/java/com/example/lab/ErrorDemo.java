package com.example.lab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期処理や @Async で例外が出たらどうなるか。 */
@Component
@ConditionalOnProperty(name = "lab.mode", havingValue = "error")
class ErrorDemo {

    private int count;
    private final AsyncWorker worker;

    ErrorDemo(AsyncWorker worker) {
        this.worker = worker;
    }

    @Component
    static class AsyncWorker {
        @Async
        void failing() {
            throw new IllegalStateException("非同期の失敗 token=SECRET-ASYNC");
        }
    }

    @Scheduled(fixedDelay = 1500)
    void failingJob() {
        count++;
        Ch29AsyncLabApplication.say("failingJob " + count + " 回目");
        throw new IllegalStateException("失敗しました token=SECRET-" + count);
    }

    @Scheduled(initialDelay = 500, fixedDelay = 60_000)
    void callAsync() {
        worker.failing();
    }
}
