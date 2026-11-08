package com.example.metricslab;

import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指標を足すときの「良いタグ」と「悪いタグ」を比べる実験。
 * QuickVote の「投票が通った回数」を数える想定。
 */
@RestController
class VoteMetricsController {

    private final MeterRegistry registry;
    // 良い例：タグの値が少数（changed / same）で、起動時に作っておける
    private final Counter changed;
    private final Counter same;

    VoteMetricsController(MeterRegistry registry) {
        this.registry = registry;
        this.changed = Counter.builder("quickvote.votes").tag("result", "changed").register(registry);
        this.same = Counter.builder("quickvote.votes").tag("result", "same").register(registry);
    }

    /** 良い例：result の値は2通りだけ。リクエストが100万回来ても、指標（時系列）は2本のまま。 */
    @GetMapping("/vote/good")
    String good(@RequestParam boolean changedVote) {
        (changedVote ? changed : same).increment();
        return "ok";
    }

    /**
     * 悪い例：投票ID をタグにする。IDの値ごとに別の指標（時系列）が1本ずつ作られ、
     * しかも Micrometer は作った指標をメモリに持ち続ける。
     */
    @GetMapping("/vote/bad")
    String bad(@RequestParam(defaultValue = "1") int times) {
        for (int i = 0; i < times; i++) {
            Counter.builder("quickvote.votes.bad")
                    .tag("pollId", UUID.randomUUID().toString())
                    .register(registry)
                    .increment();
        }
        return "created " + times;
    }
}
