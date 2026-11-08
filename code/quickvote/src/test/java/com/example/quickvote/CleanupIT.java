package com.example.quickvote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import javax.sql.DataSource;

import com.example.quickvote.common.IdempotencyRepository;
import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.invite.InviteRepository;
import com.example.quickvote.invite.InviteService;
import com.example.quickvote.poll.ExpiredDataCleaner;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.poll.PollRepository;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.support.Concurrently;
import com.example.quickvote.vote.VoteService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 期限切れデータの定期削除（ExpiredDataCleaner）。 */
class CleanupIT extends AbstractIntegrationTest {

    @Autowired
    ExpiredDataCleaner cleaner;
    @Autowired
    DataSource dataSource;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    PollRepository pollRepository;
    @Autowired
    IdempotencyRepository idempotencyRepository;
    @Autowired
    InviteRepository inviteRepository;
    @Autowired
    VoteService voteService;
    @Autowired
    InviteService inviteService;

    /** 件数の区切りを小さくしたクリーナー（バッチ10件・1回の実行で最大2バッチ）。他の部品は本物を使う。 */
    private ExpiredDataCleaner smallBatchCleaner() {
        QuickVoteProperties properties = new QuickVoteProperties(
                new QuickVoteProperties.VisitorCookie(false),
                new QuickVoteProperties.RateLimit(100, 60, 10, 10, 10, 10),
                new QuickVoteProperties.Sse(Duration.ofSeconds(15), Duration.ofMinutes(30), Duration.ofSeconds(10), 4),
                new QuickVoteProperties.Cleanup(Duration.ofMinutes(5), 10, 2),
                new QuickVoteProperties.Idempotency(Duration.ofHours(24)));
        return new ExpiredDataCleaner(dataSource, transaction, pollRepository, idempotencyRepository, inviteRepository,
                visitorRepository, properties, clock);
    }

    @Test
    void 作成から7日を過ぎた投票は削除され_関連データも一緒に消える() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true, "A", "B");
        TestVisitor member = newMember(poll);
        voteService.vote(member.id(), poll.id(), poll.options().get(0).id());
        inviteService.issue(host.id(), poll.id());

        clock.advance(Duration.ofDays(7).plusSeconds(1));
        ExpiredDataCleaner.Result result = cleaner.runOnce();

        assertThat(result.skipped()).isFalse();
        assertThat(result.polls()).isEqualTo(1);
        for (String table : new String[] { "polls", "options", "participants", "votes", "invites", "poll_events" }) {
            assertThat(countRows(table)).as(table).isZero();
        }
    }

    @Test
    void 期限内の投票は削除されない() {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");

        clock.advance(Duration.ofDays(6));
        cleaner.runOnce();

        assertThat(countRows("polls")).isEqualTo(1);
    }

    @Test
    void 締切後でも_物理削除の期限まで投票は残る() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");

        clock.set(poll.closesAt().plus(Duration.ofHours(1)));
        cleaner.runOnce();

        assertThat(countRows("polls")).isEqualTo(1);
        assertThat(pollService.get(host.id(), poll.id()).status()).isEqualTo("CLOSED");
    }

    @Test
    void 件数を区切って削除し_1回で消しきれなければ次の実行が続きから消す() {
        TestVisitor host = newVisitor();
        for (int i = 0; i < 25; i++) {
            createPoll(host, false, "A");
        }
        clock.advance(Duration.ofDays(8));
        ExpiredDataCleaner small = smallBatchCleaner();

        ExpiredDataCleaner.Result first = small.runOnce(); // 10件 × 2バッチ = 20件で打ち切り
        assertThat(first.polls()).isEqualTo(20);
        assertThat(countRows("polls")).isEqualTo(5);

        ExpiredDataCleaner.Result second = small.runOnce(); // 残り5件
        assertThat(second.polls()).isEqualTo(5);
        assertThat(countRows("polls")).isZero();
        assertThat(small.runOnce().polls()).isZero(); // 何度実行しても結果は同じ
    }

    @Test
    void 保持期限を過ぎた冪等性記録と期限切れの招待も削除される() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);
        pollService.addOption(host.id(), poll.id(), "key-1", "A");
        inviteService.issue(host.id(), poll.id());

        clock.advance(Duration.ofHours(25));
        ExpiredDataCleaner.Result result = cleaner.runOnce();

        assertThat(result.idempotencyRecords()).isEqualTo(1);
        assertThat(result.invites()).isEqualTo(1);
        assertThat(countRows("idempotency_records")).isZero();
        assertThat(countRows("polls")).isEqualTo(1); // 投票自体は7日まで残る
    }

    @Test
    void 長く使われていない訪問者も削除される() {
        TestVisitor inactive = newVisitor();
        clock.advance(Duration.ofDays(61));
        TestVisitor active = newVisitor();

        cleaner.runOnce();

        assertThat(jdbc.sql("SELECT id FROM visitors").query(Long.class).list()).containsExactly(active.id());
        assertThat(inactive.id()).isNotEqualTo(active.id());
    }

    @Test
    void 他のインスタンスがアドバイザリロックを持っている間は何もせずスキップする() throws Exception {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        clock.advance(Duration.ofDays(8));

        try (Connection other = dataSource.getConnection()) {
            // 「別のインスタンス」が削除処理の実行中（ロック保持中）という状況
            try (PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_lock(?)")) {
                lock.setLong(1, ExpiredDataCleaner.ADVISORY_LOCK_KEY);
                lock.execute();
            }
            ExpiredDataCleaner.Result skipped = cleaner.runOnce();
            assertThat(skipped.skipped()).isTrue();
            assertThat(countRows("polls")).isEqualTo(1);

            try (PreparedStatement unlock = other.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                unlock.setLong(1, ExpiredDataCleaner.ADVISORY_LOCK_KEY);
                unlock.execute();
            }
        }

        assertThat(cleaner.runOnce().polls()).isEqualTo(1);
    }

    @Test
    void 実行後はロックが解放されている_続けて何度実行してもスキップされない() {
        for (int i = 0; i < 5; i++) {
            assertThat(cleaner.runOnce().skipped()).isFalse();
        }
    }

    @Test
    void 同時に実行してもデータが壊れず_全件が消える() {
        TestVisitor host = newVisitor();
        for (int i = 0; i < 30; i++) {
            createPoll(host, false, "A");
        }
        clock.advance(Duration.ofDays(8));

        var outcomes = Concurrently.run(4, i -> () -> cleaner.runOnce());

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        // ロックを取れた回だけが削除する。スキップした回があっても、最終的に全件が消えている。
        assertThat(outcomes.stream().filter(o -> !o.value().skipped()).mapToInt(o -> o.value().polls()).sum())
                .isEqualTo(30);
        assertThat(countRows("polls")).isZero();
    }
}
