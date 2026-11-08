package com.example.quickvote.poll;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

/**
 * 第2部の Repository は、DB の代わりにメモリ上の Map を使う。メソッド名と引数は完成形（JdbcClient版）と同じにしてあり、
 * 第3部では中身（Map → SQL）だけを入れ替える。
 * <p>
 * この時点の保証: アプリを再起動すると全部消える。ConcurrentHashMap は Map 自体の破損を防ぐだけで、
 * 「確認してから更新」のまとまり（人数上限など）を守るものではない。
 */
@Repository
public class PollRepository {

    /** 参加コードの重複。DB なら UNIQUE 制約違反（DuplicateKeyException）になる場面。 */
    public static class DuplicateJoinCodeException extends RuntimeException {
        public DuplicateJoinCodeException() {
            super("join code already exists");
        }
    }

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Poll> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> idByJoinCode = new ConcurrentHashMap<>();

    public long insert(UUID publicId, String title, String joinCode, boolean allowMemberOptions, Instant closesAt,
            Instant now) {
        long id = sequence.incrementAndGet();
        // putIfAbsent は「なければ入れる」を分割できない1操作で行う（UNIQUE 制約の代わり）。
        if (idByJoinCode.putIfAbsent(joinCode, id) != null) {
            throw new DuplicateJoinCodeException();
        }
        byId.put(id, new Poll(id, publicId, title, PollStatus.OPEN, joinCode, allowMemberOptions, closesAt,
                now.plus(PollRules.RETENTION), now, 0));
        return id;
    }

    /** 手動の締切（第34章の CSRF 実験用）。 */
    public void close(long id) {
        byId.computeIfPresent(id, (k, p) -> new Poll(p.id(), p.publicId(), p.title(), PollStatus.CLOSED,
                p.joinCode(), p.allowMemberOptions(), p.closesAt(), p.deleteAfter(), p.createdAt(), p.lastEventSeq()));
    }

    public Optional<Poll> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Poll> findByPublicId(UUID publicId) {
        return byId.values().stream().filter(p -> p.publicId().equals(publicId)).findFirst();
    }

    public Optional<Poll> findByJoinCode(String joinCode) {
        Long id = idByJoinCode.get(joinCode);
        return id == null ? Optional.empty() : findById(id);
    }
}
