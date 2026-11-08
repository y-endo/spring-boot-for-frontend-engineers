package com.example.quickvote.poll;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntUnaryOperator;
import javax.sql.DataSource;

import com.example.quickvote.common.Db;
import com.example.quickvote.common.IdempotencyRepository;
import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.common.SafeThrowable;
import com.example.quickvote.invite.InviteRepository;
import com.example.quickvote.security.VisitorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 期限切れデータの定期削除。
 * <ul>
 * <li>作成から7日（delete_after）を過ぎた投票（子テーブルは CASCADE）</li>
 * <li>保持期限(24時間)を過ぎた冪等性記録</li>
 * <li>期限切れの招待</li>
 * <li>長く使われていない訪問者</li>
 * </ul>
 * 「締切後はAPIで拒否する」ことと「物理削除」は別。削除が止まっていても、締切後の投票が受け付けられることはない。
 * <p>
 * 設計: ① 1バッチごとに別トランザクションでコミットする（件数を区切る）。途中で失敗しても、
 * 消せたぶんは残り、次回の実行が続きから消す（再実行しても結果は同じ）。
 * ② 複数台で同時に走らないよう、PostgreSQL のアドバイザリロックで1台だけにする。
 */
@Component
public class ExpiredDataCleaner {

    /** アドバイザリロックのキー（アプリ内で一意な任意の数値）。 */
    public static final long ADVISORY_LOCK_KEY = 7_001_001L;

    /** Cookie は最後のアクセスから30日有効。その倍の期間アクセスのない訪問者を消す。 */
    private static final Duration VISITOR_INACTIVE = Duration.ofDays(60);

    private static final Logger log = LoggerFactory.getLogger(ExpiredDataCleaner.class);

    public record Result(boolean skipped, int polls, int idempotencyRecords, int invites, int visitors) {
        static final Result SKIPPED = new Result(true, 0, 0, 0, 0);
    }

    private final DataSource dataSource;
    private final TransactionTemplate transaction;
    private final PollRepository polls;
    private final IdempotencyRepository idempotency;
    private final InviteRepository invites;
    private final VisitorRepository visitors;
    private final QuickVoteProperties properties;
    private final Clock clock;

    public ExpiredDataCleaner(DataSource dataSource, TransactionTemplate transaction, PollRepository polls,
            IdempotencyRepository idempotency, InviteRepository invites, VisitorRepository visitors,
            QuickVoteProperties properties, Clock clock) {
        this.dataSource = dataSource;
        this.transaction = transaction;
        this.polls = polls;
        this.idempotency = idempotency;
        this.invites = invites;
        this.visitors = visitors;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${quickvote.cleanup.interval:5m}")
    public void scheduledRun() {
        try {
            Result result = runOnce();
            if (!result.skipped()) {
                log.info("期限切れデータを削除しました: polls={} idempotencyRecords={} invites={} visitors={}",
                        result.polls(), result.idempotencyRecords(), result.invites(), result.visitors());
            }
        } catch (RuntimeException e) {
            // @Scheduled のメソッドから例外を投げても次回の実行は止まらないが、ログに残すために自分で捕まえる。
            // 例外をそのまま渡すと、DB エラーのメッセージ（違反した行の中身）がログに出る。SafeThrowable で落とす。
            log.error("期限切れデータの削除に失敗しました。次回の実行で続きから削除します", SafeThrowable.of(e));
        }
    }

    /**
     * 1回ぶんの削除。テストからも直接呼べる。
     * <p>
     * pg_try_advisory_lock は「接続（セッション）」に紐づくロック。ロックを取った接続を最後まで手放さず、
     * 同じ接続で unlock する。unlock し忘れて接続をプールに返すと、その接続がロックを持ったまま残る。
     * 取れなかった（＝他のインスタンスが実行中）なら待たずにスキップする。
     */
    public Result runOnce() {
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection)) {
                log.info("他のインスタンスが削除処理を実行中のためスキップします");
                return Result.SKIPPED;
            }
            try {
                return cleanup();
            } finally {
                unlock(lockConnection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("アドバイザリロックの操作に失敗しました", e);
        }
    }

    private Result cleanup() {
        Instant now = Db.now(clock);
        QuickVoteProperties.Cleanup config = properties.cleanup();
        int deletedPolls = deleteInBatches(limit -> inTransaction(() -> polls.deleteExpiredBatch(now, limit)));
        int deletedIdempotency = deleteInBatches(limit -> inTransaction(
                () -> idempotency.deleteExpiredBatch(now.minus(properties.idempotency().retention()), limit)));
        int deletedInvites = deleteInBatches(limit -> inTransaction(() -> invites.deleteExpiredBatch(now, limit)));
        int deletedVisitors = deleteInBatches(
                limit -> inTransaction(() -> visitors.deleteInactiveBatch(now.minus(VISITOR_INACTIVE), limit)));
        log.debug("削除バッチ設定: batchSize={} maxBatchesPerRun={}", config.batchSize(), config.maxBatchesPerRun());
        return new Result(false, deletedPolls, deletedIdempotency, deletedInvites, deletedVisitors);
    }

    /** 1回の実行で消す量にも上限を置く（maxBatchesPerRun）。残りは次回の実行に回す。 */
    private int deleteInBatches(IntUnaryOperator deleteOneBatch) {
        QuickVoteProperties.Cleanup config = properties.cleanup();
        int total = 0;
        for (int i = 0; i < config.maxBatchesPerRun(); i++) {
            int deleted = deleteOneBatch.applyAsInt(config.batchSize());
            total += deleted;
            if (deleted < config.batchSize()) {
                break;
            }
        }
        return total;
    }

    private int inTransaction(java.util.function.IntSupplier work) {
        Integer deleted = transaction.execute(status -> work.getAsInt());
        return deleted == null ? 0 : deleted;
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            statement.execute();
        }
    }
}
