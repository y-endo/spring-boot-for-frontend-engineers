package com.example.quickvote.security;

import java.time.Clock;

import com.example.quickvote.common.QuickVoteProperties;

import org.jspecify.annotations.Nullable;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IP単位と訪問者単位の2軸で数える回数制限。完成形（code/quickvote）と同じ構造。
 * 違いは、実験用スイッチ lab.bucket-on-ip-reject：true にすると、元のバグ（IP単位で拒否した後も、
 * 偽 Cookie ごとに訪問者単位のバケットを作る）を再現する。
 */
@Component
@EnableScheduling
public class RateLimiter {

    private final TokenBucketLimiter perIp;
    private final TokenBucketLimiter perVisitorAttempt;
    private final TokenBucketLimiter perVisitorCreate;
    private final boolean bucketOnIpReject;

    public RateLimiter(QuickVoteProperties properties, Clock clock, LabProperties lab) {
        QuickVoteProperties.RateLimit config = properties.rateLimit();
        this.perIp = new TokenBucketLimiter(config.ipCapacity(), config.ipRefillPerMinute(), clock);
        this.perVisitorAttempt = new TokenBucketLimiter(config.visitorCapacity(), config.visitorRefillPerMinute(), clock);
        this.perVisitorCreate = new TokenBucketLimiter(config.createCapacity(), config.createRefillPerMinute(), clock);
        this.bucketOnIpReject = lab.bucketOnIpReject();
    }

    public TokenBucket.Decision checkAttempt(String ip, @Nullable String visitorKey) {
        return check(perVisitorAttempt, ip, visitorKey);
    }

    public TokenBucket.Decision checkCreate(String ip, @Nullable String visitorKey) {
        return check(perVisitorCreate, ip, visitorKey);
    }

    private TokenBucket.Decision check(TokenBucketLimiter perVisitor, String ip, @Nullable String visitorKey) {
        TokenBucket.Decision ipDecision = perIp.tryAcquire(ip);
        if (bucketOnIpReject) {
            // 元のバグ：IP で拒否されていても、訪問者単位のバケットを作ってしまう。
            TokenBucket.Decision visitorDecision = visitorKey == null ? ipDecision : perVisitor.tryAcquire(visitorKey);
            return ipDecision.allowed() ? visitorDecision : ipDecision;
        }
        if (!ipDecision.allowed() || visitorKey == null) {
            return ipDecision;
        }
        return perVisitor.tryAcquire(visitorKey);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictIdle() {
        perIp.evictIdle();
        perVisitorAttempt.evictIdle();
        perVisitorCreate.evictIdle();
    }

    public int visitorBucketCount() {
        return perVisitorAttempt.size() + perVisitorCreate.size();
    }

    public int ipBucketCount() {
        return perIp.size();
    }
}
