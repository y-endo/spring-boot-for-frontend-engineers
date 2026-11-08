package com.example.quickvote.security;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 実験用：訪問者の数と、回数制限のバケットの数を見る（第35章）。完成形（code/quickvote）にはない。
 * 本番にこういう「内部の数」を公開する API を置いてはいけない（完成形の Actuator は管理ポートに分けてある）。
 */
@RestController
public class LabStatsController {

    private final VisitorRepository visitors;
    private final RateLimiter rateLimiter;

    public LabStatsController(VisitorRepository visitors, RateLimiter rateLimiter) {
        this.visitors = visitors;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/lab/stats")
    public Map<String, Integer> stats() {
        return Map.of(
                "visitors", visitors.count(),
                "ipBuckets", rateLimiter.ipBucketCount(),
                "visitorBuckets", rateLimiter.visitorBucketCount());
    }
}
