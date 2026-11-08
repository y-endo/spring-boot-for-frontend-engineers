package com.example.quickvote.security;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

/**
 * visitors 表のメモリ版。完成形（code/quickvote）は JdbcClient で visitors 表（token_hash を UNIQUE）を引く。
 * どちらも「トークンそのものは保存せず、SHA-256 のハッシュだけを持つ」。メソッド名と引数は完成形と同じ。
 */
@Repository
public class VisitorRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<String, Long> idByTokenHash = new ConcurrentHashMap<>();

    public Optional<Long> findIdByTokenHash(String tokenHash) {
        return Optional.ofNullable(idByTokenHash.get(tokenHash));
    }

    public long create(String tokenHash) {
        long id = sequence.incrementAndGet();
        idByTokenHash.put(tokenHash, id);
        return id;
    }

    /** 実験用：訪問者の数。不正なリクエストで行が増え続けないことの確認に使う。 */
    public int count() {
        return idByTokenHash.size();
    }
}
