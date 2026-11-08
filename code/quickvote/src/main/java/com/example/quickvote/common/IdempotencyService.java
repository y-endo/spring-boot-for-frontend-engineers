package com.example.quickvote.common;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key による再送の吸収。
 * <p>
 * 方式: 業務更新と「同じトランザクション」で idempotency_records を書く。
 * <ol>
 * <li>まず (訪問者, キー) の行を INSERT ... ON CONFLICT DO NOTHING で先取りする。</li>
 * <li>先取りできたら（1件挿入）→ 業務処理を実行し、応答を同じ行に保存する。</li>
 * <li>先取りできなかったら（0件）→ 他のトランザクションが確定済み。保存済みの応答を（行を共有ロックして）読んで返す。
 * 同時に来た場合は、先行側がコミットするまで INSERT が待たされるので、後続は必ず確定後の応答を読む。
 * 読もうとしたときに、定期削除が記録を消していた場合は、先取りからやり直す。</li>
 * </ol>
 * 業務処理が例外で失敗したらトランザクションごとロールバックされ、キーの記録も残らない。
 * つまり「失敗した試行はなかったこと」になり、同じキーで再送すれば再実行される（失敗応答は保存しない）。
 * <p>
 * 別トランザクションで先に記録を確定させる方式（IN_PROGRESS 行を作る）は、
 * 「記録だけ残って業務更新がない」状態や、クラッシュ後の掃除が必要になるため採らなかった。
 */
@Service
public class IdempotencyService {

    /** 先取りからのやり直しの上限。やり直しは「記録が消えた」場合だけなので、通常は2回目で決着する。 */
    private static final int MAX_ATTEMPTS = 5;

    public record Result<T>(int status, T body, boolean replayed) {
    }

    private final IdempotencyRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final QuickVoteProperties properties;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper jsonMapper, Clock clock,
            QuickVoteProperties properties) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * MANDATORY: 呼び出し元がトランザクションを開始していなければ例外にする。
     * 業務更新と別のトランザクションで動くと、この方式の前提が崩れるため。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Result<T> execute(long visitorId, String key, String requestHash, int successStatus,
            Class<T> bodyType, Supplier<T> action) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Instant now = Db.now(clock);
            repository.deleteExpired(visitorId, key, now.minus(properties.idempotency().retention()));

            if (repository.tryInsert(visitorId, key, requestHash, now) == 1) {
                T body = action.get();
                repository.complete(visitorId, key, successStatus, jsonMapper.writeValueAsString(body));
                return new Result<>(successStatus, body, false);
            }

            // 先取りできなかった = 確定済みの記録がある、はず。しかし READ COMMITTED では SQL 文ごとに見える状態が変わる。
            // 「0件だった INSERT」と「次の SELECT」の間に、定期削除が期限切れの記録を消してコミットすることがある。
            // そのときは記録が見つからない。エラーにせず、先取りからやり直す（今度は自分が最初の実行者になれる）。
            Optional<IdempotencyRepository.Record> saved = repository.findForShare(visitorId, key);
            if (saved.isEmpty()) {
                continue;
            }
            if (!saved.get().requestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new Result<>(saved.get().status(), jsonMapper.readValue(saved.get().responseBody(), bodyType), true);
        }
        throw new IllegalStateException("冪等性記録の先取りを繰り返しても確定できなかった");
    }
}
