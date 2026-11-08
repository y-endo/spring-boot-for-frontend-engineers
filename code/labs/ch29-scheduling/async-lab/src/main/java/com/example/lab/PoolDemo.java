package com.example.lab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** @Scheduled のスレッド数。遅い定期処理が、ほかの定期処理を待たせるか。 */
@Component
@ConditionalOnProperty(name = "lab.mode", havingValue = "pool")
class PoolDemo {

    @Scheduled(fixedDelay = 1000)
    void heartbeat() {
        Ch29AsyncLabApplication.say("heartbeat（1秒ごとのはずの軽い処理）");
    }

    @Scheduled(initialDelay = 2500, fixedDelay = 60_000)
    void slowCleanup() throws InterruptedException {
        Ch29AsyncLabApplication.say("slowCleanup 開始（4秒かかる）");
        Thread.sleep(4000);
        Ch29AsyncLabApplication.say("slowCleanup 終了");
    }
}
