package com.example.review2.fixed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** 訪問者トークン（32バイトの乱数）→ 訪問者ID。保存するのは SHA-256 のハッシュだけ。 */
@Component
public class VisitorSessions {

    private final SecureRandom random = new SecureRandom();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Long> byHash = new ConcurrentHashMap<>();

    /** 新しい訪問者を作り、Cookie に入れるトークンを返す。 */
    public String issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byHash.put(sha256(token), sequence.incrementAndGet());
        return token;
    }

    public Optional<Long> find(String token) {
        return Optional.ofNullable(byHash.get(sha256(token)));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
