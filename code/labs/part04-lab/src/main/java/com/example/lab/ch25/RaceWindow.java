package com.example.lab.ch25;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 「数えた」と「挿入した」のあいだに、わざと隙間を作る道具。
 * 本物のコードには要らない。競合を「運」に頼らず、狙って起こすための実験用の仕掛け。
 * <ul>
 * <li>lab.race-delay-ms: HTTP 経由の実験で、隙間を何ミリ秒にするか（既定 0 = 隙間を作らない）</li>
 * <li>setHook: テストで、隙間にラッチ（CountDownLatch）などを挟む</li>
 * </ul>
 */
@Component
public class RaceWindow {

    private final long delayMillis;
    private volatile Runnable hook;

    public RaceWindow(@Value("${lab.race-delay-ms:0}") long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public void setHook(Runnable hook) {
        this.hook = hook;
    }

    public void clearHook() {
        this.hook = null;
    }

    public void pass() {
        Runnable h = hook;
        if (h != null) {
            h.run();
            return;
        }
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
