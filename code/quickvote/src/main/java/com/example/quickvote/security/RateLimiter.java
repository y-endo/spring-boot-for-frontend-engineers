package com.example.quickvote.security;

import java.time.Clock;

import com.example.quickvote.common.QuickVoteProperties;

import org.jspecify.annotations.Nullable;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 誰でも叩ける入口の回数制限。IP単位と訪問者単位の2軸で数える。
 * <ul>
 * <li>IP単位（全入口で共通）: Cookie を毎回捨てて新しい訪問者になりすます攻撃を止める。
 * 訪問者の行は検証より前に作られるので、入口ごとに IP の制限がないと、不正なリクエストでも行を増やし続けられる</li>
 * <li>訪問者単位: 同じ Cookie で、IP を変えながら試す攻撃を止める。
 * 参加コード・招待トークンの総当たり（attempt）と、投票・招待の作成（create）で別々に数える</li>
 * </ul>
 * どちらか一方でも空なら拒否。成功・失敗にかかわらず「試行」として数える。
 */
@Component
public class RateLimiter {

    private final TokenBucketLimiter perIp;
    private final TokenBucketLimiter perVisitorAttempt;
    private final TokenBucketLimiter perVisitorCreate;

    public RateLimiter(QuickVoteProperties properties, Clock clock) {
        QuickVoteProperties.RateLimit config = properties.rateLimit();
        this.perIp = new TokenBucketLimiter(config.ipCapacity(), config.ipRefillPerMinute(), clock);
        this.perVisitorAttempt = new TokenBucketLimiter(config.visitorCapacity(), config.visitorRefillPerMinute(), clock);
        this.perVisitorCreate = new TokenBucketLimiter(config.createCapacity(), config.createRefillPerMinute(), clock);
    }

    /** 参加コード・招待トークンを試す入口（POST /api/join、招待の受諾）。 */
    public TokenBucket.Decision checkAttempt(String ip, @Nullable String visitorKey) {
        return check(perVisitorAttempt, ip, visitorKey);
    }

    /** 投票・招待を作る入口。 */
    public TokenBucket.Decision checkCreate(String ip, @Nullable String visitorKey) {
        return check(perVisitorCreate, ip, visitorKey);
    }

    /**
     * IP単位 → 訪問者単位の順に判定する。IP単位で拒否したら、そこで終わる（訪問者単位のバケットを作らない）。
     * 続けると、Cookie の値を変えながら送るだけで、拒否されているのに訪問者ごとのバケットがメモリに増え続ける。
     *
     * @param ip         接続元IP
     * @param visitorKey Cookie の訪問者トークンのハッシュ。Cookie がなければ（形式が違う場合も）null で、IP単位だけで数える
     * @return 許可なら Decision.allowed() が true。拒否なら、待つべき時間（拒否した側の分）
     */
    private TokenBucket.Decision check(TokenBucketLimiter perVisitor, String ip, @Nullable String visitorKey) {
        TokenBucket.Decision ipDecision = perIp.tryAcquire(ip);
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

    /** 監視・テスト用。訪問者単位のバケットの数。 */
    public int visitorBucketCount() {
        return perVisitorAttempt.size() + perVisitorCreate.size();
    }

    /** 監視・テスト用。IP単位のバケットの数。 */
    public int ipBucketCount() {
        return perIp.size();
    }
}
