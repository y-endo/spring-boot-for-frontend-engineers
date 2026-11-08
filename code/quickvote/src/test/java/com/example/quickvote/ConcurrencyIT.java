package com.example.quickvote;

import java.util.ArrayList;
import java.util.List;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.invite.InviteResponse;
import com.example.quickvote.invite.InviteService;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.support.Concurrently;
import com.example.quickvote.support.Concurrently.Outcome;
import com.example.quickvote.vote.VoteRepository;
import com.example.quickvote.vote.VoteService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同時実行のテスト。全スレッドを CountDownLatch で揃えて一斉に開始し、
 * 「何回か動かして通った」ではなく「結果が必ず満たすべき条件（不変条件）」を確かめる。
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    VoteService voteService;
    @Autowired
    VoteRepository voteRepository;
    @Autowired
    InviteService inviteService;

    private static boolean failedWith(Outcome<?> outcome, ErrorCode code) {
        return outcome.error() instanceof ApiException e && e.code() == code;
    }

    @Test
    void 参加者49人の投票に20人が同時に参加しても成功はちょうど1人() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        addMembersDirectly(poll, 48); // 主催者＋48人＝49人（残り1枠）
        List<TestVisitor> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(newVisitor());
        }

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> joinService.join(candidates.get(i).id(),
                poll.joinCode()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> failedWith(o, ErrorCode.POLL_FULL)).hasSize(19);
        assertThat(countRows("participants")).isEqualTo(50);
    }

    @Test
    void 選択肢が8件の投票に20人が同時に追加しても10件を超えない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true, "1", "2", "3", "4", "5", "6", "7", "8");
        List<TestVisitor> members = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            members.add(newMember(poll));
        }

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> pollService.addOption(
                members.get(i).id(), poll.id(), "key-" + i, "追加" + i));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(2);
        assertThat(outcomes).filteredOn(o -> failedWith(o, ErrorCode.OPTION_LIMIT_REACHED)).hasSize(18);
        assertThat(countRows("options")).isEqualTo(10);
        // 失敗したリクエストの冪等性記録は残らない（トランザクションごとロールバックされる）
        assertThat(countRows("idempotency_records")).isEqualTo(2);
    }

    @Test
    void 同じ人が20回同時に投票しても票は1行() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B", "C", "D", "E");
        TestVisitor member = newMember(poll);

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> voteService.vote(member.id(), poll.id(),
                poll.options().get(i % 5).id()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(countRows("votes")).isEqualTo(1);
    }

    @Test
    void 親行のロックを使わずに更新しても_UNIQUE制約とON_CONFLICTで票は1行() {
        // 1人1票の「最終防衛線」だけを単独で確かめる。VoteService のロックを通さず、リポジトリを直接叩く。
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B", "C", "D", "E");
        long pollId = jdbc.sql("SELECT id FROM polls").query(Long.class).single();
        long participantId = jdbc.sql("SELECT id FROM participants").query(Long.class).single();

        List<Outcome<Object>> outcomes = Concurrently.run(20, i -> () -> voteRepository.upsert(pollId, participantId,
                poll.options().get(i % 5).id(), clock.instant()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(countRows("votes")).isEqualTo(1);
    }

    @Test
    void 大勢が同時に投票しても票は人数分で_イベント連番は欠けも重複もない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B");
        List<TestVisitor> members = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            members.add(newMember(poll));
        }

        Concurrently.run(30, i -> () -> voteService.vote(members.get(i).id(), poll.id(),
                poll.options().get(i % 2).id()));

        assertThat(countRows("votes")).isEqualTo(30);
        // 参加30回＋投票30回＝60イベント。連番は 1..60 がちょうど1回ずつ。
        List<Long> seqs = jdbc.sql("SELECT seq FROM poll_events ORDER BY seq").query(Long.class).list();
        assertThat(seqs).hasSize(60);
        for (int i = 0; i < seqs.size(); i++) {
            assertThat(seqs.get(i)).isEqualTo(i + 1L);
        }
        long lastSeq = jdbc.sql("SELECT last_event_seq FROM polls").query(Long.class).single();
        assertThat(lastSeq).isEqualTo(60);
    }

    @Test
    void 同じ招待トークンを10人が同時に受けても成功は1人() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        List<TestVisitor> invitees = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            invitees.add(newVisitor());
        }

        List<Outcome<Object>> outcomes = Concurrently.run(10, i -> () -> inviteService.accept(invitees.get(i).id(),
                invite.token()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> failedWith(o, ErrorCode.INVITE_INVALID)).hasSize(9);
        assertThat(jdbc.sql("SELECT count(*) FROM participants WHERE role = 'HOST'").query(Long.class).single())
                .isEqualTo(2); // 作成者＋招待で加わった1人
        assertThat(jdbc.sql("SELECT count(*) FROM invites WHERE used_at IS NOT NULL").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void 人数上限で招待の受諾が失敗したら_招待は使われないまま残る() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        addMembersDirectly(poll, 49); // 主催者＋49人＝50人（満員）
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor invitee = newVisitor();

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> inviteService.accept(invitee.id(), invite.token()));

        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.POLL_FULL));
        // 消費(UPDATE)と参加者の追加は同じトランザクション。失敗したので消費も取り消されている。
        assertThat(jdbc.sql("SELECT count(*) FROM invites WHERE used_at IS NULL").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 満員の投票でも_すでに参加している人の再参加は成功する() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        TestVisitor member = newMember(poll);
        addMembersDirectly(poll, 48); // 合計50人

        assertThat(joinService.join(member.id(), poll.joinCode()).pollId()).isEqualTo(poll.id());
        assertThat(countRows("participants")).isEqualTo(50);
    }
}
