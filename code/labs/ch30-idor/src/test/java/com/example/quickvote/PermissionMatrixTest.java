package com.example.quickvote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.example.quickvote.poll.PollRepository;
import com.example.quickvote.support.TestClockConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 権限表をそのままテストにしたもの（第30章）。メモリ版のラボなので、操作は GET・選択肢追加・投票の3つ。
 * 完成形の PermissionMatrixIT は、これに締切・削除・招待発行を足した6操作を実 PostgreSQL で確かめる。
 * <p>
 * idor プロファイルで、わざと認可を忘れた API（v0）と直した API（v1）も確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@ActiveProfiles("idor")
class PermissionMatrixTest {

    enum Actor { OUTSIDER, MEMBER, HOST }

    enum Operation { GET_POLL, ADD_OPTION, VOTE }

    private static final AtomicLong NEXT_VISITOR = new AtomicLong(5000);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    PollRepository polls;

    static Stream<Arguments> matrix() {
        return Stream.of(
                // 操作                      非参加者  MEMBER   HOST
                row(Operation.GET_POLL, 404, 200, 200),
                row(Operation.ADD_OPTION, 404, 403, 201), // allowMemberOptions=false の投票
                row(Operation.VOTE, 404, 200, 200)).flatMap(s -> s);
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
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> poll = createPoll(host);
        long member = NEXT_VISITOR.incrementAndGet();
        join(member, (String) poll.get("joinCode"));
        long outsider = NEXT_VISITOR.incrementAndGet();
        long visitor = switch (actor) {
            case HOST -> host;
            case MEMBER -> member;
            case OUTSIDER -> outsider;
        };
        String base = "/api/polls/" + poll.get("id");

        MvcTestResult result = switch (operation) {
            case GET_POLL -> mvc.get().uri(base).header("X-Visitor-Id", String.valueOf(visitor)).exchange();
            case ADD_OPTION -> send(visitor, HttpMethod.POST, base + "/options", "{\"label\":\"うどん\"}");
            case VOTE -> send(visitor, HttpMethod.PUT, base + "/my-vote", "{\"optionId\":" + firstOptionId(poll) + "}");
        };

        assertThat(result).hasStatus(HttpStatus.valueOf(expectedStatus));
    }

    @Test
    void 認可を忘れたv0は_非参加者にも参加コードを返してしまう() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> poll = createPoll(host);
        long outsider = NEXT_VISITOR.incrementAndGet();

        MvcTestResult result = mvc.get().uri("/api/v0/polls/" + internalId(poll))
                .header("X-Visitor-Id", String.valueOf(outsider)).exchange();

        // この assert が通る＝穴がある。権限表のテストに v0 の行を足すと、ここが赤くなる。
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains((String) poll.get("joinCode"));
    }

    @Test
    void 直したv1は_非参加者には存在しない投票と同じ404() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> poll = createPoll(host);
        long outsider = NEXT_VISITOR.incrementAndGet();

        MvcTestResult exists = mvc.get().uri("/api/v1/polls/" + internalId(poll))
                .header("X-Visitor-Id", String.valueOf(outsider)).exchange();
        MvcTestResult missing = mvc.get().uri("/api/v1/polls/99999")
                .header("X-Visitor-Id", String.valueOf(outsider)).exchange();

        assertThat(exists).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- ヘルパー ----

    @SuppressWarnings("unchecked")
    private static long firstOptionId(Map<String, Object> poll) {
        return ((Number) ((java.util.List<Map<String, Object>>) poll.get("options")).get(0).get("id")).longValue();
    }

    /** 内部ID（連番）。API の応答には出ない値なので、Repository から引く。 */
    private long internalId(Map<String, Object> poll) {
        return polls.findByPublicId(UUID.fromString((String) poll.get("id"))).orElseThrow().id();
    }

    private Map<String, Object> createPoll(long host) {
        MvcTestResult created = send(host, HttpMethod.POST, "/api/polls",
                "{\"title\":\"秘密の投票\",\"closesInMinutes\":30,\"allowMemberOptions\":false,\"options\":[\"A\",\"B\"]}");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return read(created);
    }

    private void join(long visitor, String code) {
        assertThat(send(visitor, HttpMethod.POST, "/api/join", "{\"code\":\"" + code + "\"}")).hasStatusOk();
    }

    private MvcTestResult send(long visitor, HttpMethod method, String uri, String body) {
        return mvc.method(method).uri(uri)
                .header("X-Visitor-Id", String.valueOf(visitor))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(MvcTestResult result) {
        return json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }
}
