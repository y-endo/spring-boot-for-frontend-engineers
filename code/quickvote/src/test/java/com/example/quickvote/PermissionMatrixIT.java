package com.example.quickvote;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 権限表（非参加者 / MEMBER / HOST × 主要操作）をそのままテストにしたもの。
 * 非参加者には「存在しない投票」と同じ404を返す（存在を漏らさない）。
 * 参加者だが権限が足りないときは403（存在は知っているので隠す意味がない）。
 */
class PermissionMatrixIT extends AbstractIntegrationTest {

    enum Actor { OUTSIDER, MEMBER, HOST }

    enum Operation { GET_POLL, ADD_OPTION, VOTE, CLOSE, DELETE, ISSUE_INVITE }

    static Stream<Arguments> matrix() {
        return Stream.of(
                // 操作                      非参加者  MEMBER   HOST
                row(Operation.GET_POLL, 404, 200, 200),
                row(Operation.ADD_OPTION, 404, 403, 201), // allowMemberOptions=false の投票
                row(Operation.VOTE, 404, 200, 200),
                row(Operation.CLOSE, 404, 403, 204),
                row(Operation.DELETE, 404, 403, 204),
                row(Operation.ISSUE_INVITE, 404, 403, 201)).flatMap(s -> s);
    }

    private static Stream<Arguments> row(Operation op, int outsider, int member, int host) {
        return Stream.of(
                Arguments.of(op, Actor.OUTSIDER, outsider),
                Arguments.of(op, Actor.MEMBER, member),
                Arguments.of(op, Actor.HOST, host));
    }

    @ParameterizedTest(name = "{0} を {1} が実行すると {2}")
    @MethodSource("matrix")
    void 権限表どおりの応答になる(Operation operation, Actor actor, int expectedStatus) {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "カレー", "ラーメン");
        TestVisitor member = newMember(poll);
        TestVisitor outsider = newVisitor();
        TestVisitor visitor = switch (actor) {
            case HOST -> host;
            case MEMBER -> member;
            case OUTSIDER -> outsider;
        };
        String base = "/api/polls/" + poll.id();
        long optionId = poll.options().get(0).id();

        MvcTestResult result = switch (operation) {
            case GET_POLL -> request(HttpMethod.GET, base, visitor, null);
            case ADD_OPTION -> request(HttpMethod.POST, base + "/options", visitor, Map.of("label", "うどん"),
                    "Idempotency-Key", UUID.randomUUID().toString());
            case VOTE -> request(HttpMethod.PUT, base + "/my-vote", visitor, Map.of("optionId", optionId));
            case CLOSE -> request(HttpMethod.POST, base + "/close", visitor, null);
            case DELETE -> request(HttpMethod.DELETE, base, visitor, null);
            case ISSUE_INVITE -> request(HttpMethod.POST, base + "/invites", visitor, null);
        };

        assertThat(result).hasStatus(HttpStatus.valueOf(expectedStatus));
    }

    @Test
    void 許可された投票ではMEMBERも選択肢を追加できる() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true, "カレー");
        TestVisitor member = newMember(poll);

        MvcTestResult result = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", member,
                Map.of("label", "うどん"), "Idempotency-Key", UUID.randomUUID().toString());

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 存在しない投票と参加していない投票は区別できない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "カレー");
        TestVisitor outsider = newVisitor();

        MvcTestResult notParticipant = request(HttpMethod.GET, "/api/polls/" + poll.id(), outsider, null);
        MvcTestResult notExisting = request(HttpMethod.GET, "/api/polls/" + UUID.randomUUID(), outsider, null);

        assertThat(notParticipant).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notExisting).hasStatus(HttpStatus.NOT_FOUND);
        // ステータスだけでなく code と detail も同じ。instance（要求したパス）だけが違う。
        Map<?, ?> a = bodyAs(notParticipant, Map.class);
        Map<?, ?> b = bodyAs(notExisting, Map.class);
        assertThat(a.get("code")).isEqualTo(b.get("code")).isEqualTo("POLL_NOT_FOUND");
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
    }

    @Test
    void 他の投票の選択肢IDを指定して投票しても404() {
        TestVisitor host = newVisitor();
        PollDetail pollA = createPoll(host, false, "A");
        PollDetail pollB = createPoll(host, false, "B");

        MvcTestResult result = request(HttpMethod.PUT, "/api/polls/" + pollA.id() + "/my-vote", host,
                Map.of("optionId", pollB.options().get(0).id()));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_NOT_FOUND");
    }

    @Test
    void 招待で共同主催者になった人は主催者の操作ができる() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "カレー");
        String token = bodyAs(request(HttpMethod.POST, "/api/polls/" + poll.id() + "/invites", host, null),
                Map.class).get("token").toString();
        TestVisitor coHost = newVisitor();

        assertThat(request(HttpMethod.POST, "/api/invites/" + token + "/accept", coHost, null)).hasStatusOk();

        assertThat(request(HttpMethod.POST, "/api/polls/" + poll.id() + "/close", coHost, null))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void 参加していない人は参加者の人数や状態を知る手がかりを得られない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "カレー");
        TestVisitor outsider = newVisitor();

        // 投票・締切・削除・招待のどれも、非参加者に副作用を起こさない
        request(HttpMethod.POST, "/api/polls/" + poll.id() + "/close", outsider, null);
        request(HttpMethod.DELETE, "/api/polls/" + poll.id(), outsider, null);

        PollDetail after = bodyAs(request(HttpMethod.GET, "/api/polls/" + poll.id(), host, null), PollDetail.class);
        assertThat(after.status()).isEqualTo("OPEN");
        assertThat(after.participantCount()).isEqualTo(1);
    }
}
