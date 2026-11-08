package com.example.review2.infra;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** 招待トークン置き場（演習用）。トークン → 投票ID。一度使ったら消える。 */
@Component
public class Invites {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public String issue(long pollId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, pollId);
        return token;
    }

    /** 原子的に取り出す（二重に使えない）。 */
    public Optional<Long> consume(String token) {
        return Optional.ofNullable(tokens.remove(token));
    }
}
