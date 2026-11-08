package com.example.quickvote;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.invite.InviteResponse;
import com.example.quickvote.invite.InviteService;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.vote.VoteService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 時間に関する条件。Clock を進めて、締切の境界・手動締切・招待の期限を確かめる。 */
class DeadlineIT extends AbstractIntegrationTest {

    @Autowired
    VoteService voteService;
    @Autowired
    InviteService inviteService;

    private static void assertRejected(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void 締切時刻の直前までは投票でき_締切時刻ちょうどから拒否される() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A", "B");
        long optionA = poll.options().get(0).id();
        long optionB = poll.options().get(1).id();
        Instant closesAt = poll.closesAt();

        clock.set(closesAt.minus(Duration.ofNanos(1_000))); // 1マイクロ秒前
        voteService.vote(host.id(), poll.id(), optionA);

        clock.set(closesAt); // 締切ちょうど（closes_at は含まない半開区間）
        Throwable atDeadline = catchThrowable(() -> voteService.vote(host.id(), poll.id(), optionB));
        assertRejected(atDeadline, ErrorCode.POLL_CLOSED);

        clock.set(closesAt.plus(Duration.ofDays(1)));
        Throwable after = catchThrowable(() -> voteService.vote(host.id(), poll.id(), optionB));
        assertRejected(after, ErrorCode.POLL_CLOSED);
        // 拒否された投票は反映されない
        assertThat(jdbc.sql("SELECT option_id FROM votes").query(Long.class).single()).isEqualTo(optionA);
    }

    @Test
    void 締切後は投票_選択肢追加_新規参加のどれも拒否され_詳細は閉鎖と表示される() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true, "A");
        clock.set(poll.closesAt().plusSeconds(1));
        TestVisitor late = newVisitor();

        assertRejected(catchThrowable(() -> pollService.addOption(host.id(), poll.id(), "k", "B")), ErrorCode.POLL_CLOSED);
        assertRejected(catchThrowable(() -> joinService.join(late.id(), poll.joinCode())), ErrorCode.POLL_CLOSED);
        assertThat(pollService.get(host.id(), poll.id()).status()).isEqualTo("CLOSED");
    }

    @Test
    void 締切前の詳細は_OPEN_で_時間が進むと_CLOSED_に変わる() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");

        assertThat(pollService.get(host.id(), poll.id()).status()).isEqualTo("OPEN");
        clock.set(poll.closesAt());

        assertThat(pollService.get(host.id(), poll.id()).status()).isEqualTo("CLOSED");
    }

    @Test
    void 主催者が締切を早めると_以降の投票は拒否され_二重に締めてもイベントは増えない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        long optionA = poll.options().get(0).id();

        pollService.close(host.id(), poll.id());
        pollService.close(host.id(), poll.id()); // 再送しても結果は同じ

        assertRejected(catchThrowable(() -> voteService.vote(host.id(), poll.id(), optionA)), ErrorCode.POLL_CLOSED);
        assertThat(countRows("poll_events")).isEqualTo(1); // POLL_CLOSED は1回だけ
        Instant closesAt = jdbc.sql("SELECT closes_at FROM polls").query((rs, n) ->
                rs.getObject(1, java.time.OffsetDateTime.class).toInstant()).single();
        assertThat(closesAt).isBefore(poll.closesAt()); // 締切時刻も前倒しされる
    }

    @Test
    void 招待は期限ちょうどから使えなくなる() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor invitee = newVisitor();
        assertThat(invite.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));

        clock.set(invite.expiresAt()); // WHERE expires_at > now が偽になる
        assertRejected(catchThrowable(() -> inviteService.accept(invitee.id(), invite.token())), ErrorCode.INVITE_INVALID);
    }

    @Test
    void 招待は期限の直前までは使える() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, Duration.ofDays(3), "A"); // 招待の期限(24時間)を過ぎても締切にならない長さ
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor invitee = newVisitor();

        clock.set(invite.expiresAt().minus(Duration.ofNanos(1_000)));

        assertThat(inviteService.accept(invitee.id(), invite.token()).pollId()).isEqualTo(poll.id());
    }

    @Test
    void 使用済みの招待は再利用できない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor first = newVisitor();
        TestVisitor second = newVisitor();

        inviteService.accept(first.id(), invite.token());

        assertRejected(catchThrowable(() -> inviteService.accept(second.id(), invite.token())), ErrorCode.INVITE_INVALID);
        // 存在しないトークンも同じ応答（理由を区別して教えない）
        assertRejected(catchThrowable(() -> inviteService.accept(second.id(), "unknown-token")), ErrorCode.INVITE_INVALID);
    }

    @Test
    void 締切後の招待受諾は拒否され_招待は未消費のまま残る() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor invitee = newVisitor();
        long participantsBefore = countRows("participants");
        long eventsBefore = countRows("poll_events");

        clock.set(poll.closesAt()); // 招待の期限(24時間)の前に、投票が締め切られる

        assertRejected(catchThrowable(() -> inviteService.accept(invitee.id(), invite.token())), ErrorCode.POLL_CLOSED);
        // 拒否されたとき、招待は消費されていない（消費はロールバックされる）。参加者もイベントも増えない。
        assertThat(jdbc.sql("SELECT used_at IS NULL AND used_by IS NULL FROM invites").query(Boolean.class).single())
                .isTrue();
        assertThat(countRows("participants")).isEqualTo(participantsBefore);
        assertThat(countRows("poll_events")).isEqualTo(eventsBefore);
    }

    @Test
    void 手動で締め切った投票にも_招待では参加できない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, Duration.ofDays(3), "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor invitee = newVisitor();
        pollService.close(host.id(), poll.id());

        assertRejected(catchThrowable(() -> inviteService.accept(invitee.id(), invite.token())), ErrorCode.POLL_CLOSED);
        assertThat(jdbc.sql("SELECT used_at IS NULL FROM invites").query(Boolean.class).single()).isTrue();
    }

    @Test
    void 締切後は_すでに参加している人の昇格も拒否される() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        TestVisitor member = newMember(poll);
        InviteResponse invite = inviteService.issue(host.id(), poll.id());

        clock.set(poll.closesAt());

        assertRejected(catchThrowable(() -> inviteService.accept(member.id(), invite.token())), ErrorCode.POLL_CLOSED);
        assertThat(jdbc.sql("SELECT role FROM participants WHERE visitor_id = :v").param("v", member.id())
                .query(String.class).single()).isEqualTo("MEMBER");
    }

    @Test
    void 締切後でも_無効な招待は投票の状態を教えず_INVITE_INVALIDのまま() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        TestVisitor first = newVisitor();
        inviteService.accept(first.id(), invite.token()); // 締切前に使用済みにする

        clock.set(poll.closesAt());

        assertRejected(catchThrowable(() -> inviteService.accept(newVisitor().id(), invite.token())),
                ErrorCode.INVITE_INVALID);
    }

    @Test
    void 締切後の招待受諾はHTTPでは409_POLL_CLOSED() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        InviteResponse invite = inviteService.issue(host.id(), poll.id());
        clock.set(poll.closesAt());

        var result = request(HttpMethod.POST, "/api/invites/" + invite.token() + "/accept", newVisitor(), null);

        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("POLL_CLOSED");
    }

    @Test
    void 締切後でもHTTPでは409のProblemDetailが返る() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        clock.set(poll.closesAt());

        var result = request(HttpMethod.PUT, "/api/polls/" + poll.id() + "/my-vote", host,
                Map.of("optionId", poll.options().get(0).id()));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }
}
