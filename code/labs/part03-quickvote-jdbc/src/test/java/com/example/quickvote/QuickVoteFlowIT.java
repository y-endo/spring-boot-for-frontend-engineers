package com.example.quickvote;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * アプリ全体（本物の Controller・Service・Repository）を起動して、HTTP の入口から通しで確かめるテスト。
 * 実際のポートは開かず、MockMvc で DispatcherServlet に直接リクエストを渡す。
 * Repository は実 PostgreSQL（Testcontainers）につながっていて、スキーマは Flyway が作る。
 * テストごとに全テーブルを空にする（AbstractIntegrationTest）ので、順番に依存しない。
 */
@AutoConfigureMockMvc
class QuickVoteFlowIT extends AbstractIntegrationTest {

    /** 訪問者IDは数値（第6部で Cookie に替わるまでの暫定）。テストごとに別の値を使う。 */
    private static final AtomicLong NEXT_VISITOR = new AtomicLong(1000);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Test
    void 作成_参加_投票_取得を通しで確かめる() {
        long host = NEXT_VISITOR.incrementAndGet();
        long member = NEXT_VISITOR.incrementAndGet();

        Map<String, Object> created = body(create(host, "ランチどこ行く？", 30, true, "カレー", "ラーメン"), HttpStatus.CREATED);
        String pollId = (String) created.get("id");
        String joinCode = (String) created.get("joinCode");
        assertThat(created.get("role")).isEqualTo("HOST");
        assertThat(joinCode).hasSize(8);

        Map<String, Object> joined = body(post(member, "/api/join", "{\"code\":\"" + joinCode.toLowerCase() + " \"}"),
                HttpStatus.OK);
        assertThat(joined).containsEntry("pollId", pollId).containsEntry("role", "MEMBER");

        long curry = optionId(created, "カレー");
        assertThat(put(member, "/api/polls/" + pollId + "/my-vote", "{\"optionId\":" + curry + "}"))
                .hasStatus(HttpStatus.OK);

        Map<String, Object> seenByHost = body(get(host, pollId), HttpStatus.OK);
        assertThat(seenByHost).containsEntry("participantCount", 2).containsEntry("totalVotes", 1);
        Map<String, Object> seenByMember = body(get(member, pollId), HttpStatus.OK);
        assertThat(seenByMember).containsEntry("role", "MEMBER").containsEntry("myVoteOptionId", (int) curry);
        assertThat(seenByMember.get("joinCode")).as("参加コードは主催者にだけ見せる").isNull();
    }

    @Test
    void 再投票は上書きで_得票率の合計は100になる() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> created = body(create(host, "3択", 30, false, "A", "B", "C"), HttpStatus.CREATED);
        String pollId = (String) created.get("id");
        long a = optionId(created, "A");
        long b = optionId(created, "B");

        put(host, "/api/polls/" + pollId + "/my-vote", "{\"optionId\":" + a + "}");
        put(host, "/api/polls/" + pollId + "/my-vote", "{\"optionId\":" + b + "}");

        Map<String, Object> detail = body(get(host, pollId), HttpStatus.OK);
        assertThat(detail).containsEntry("totalVotes", 1);
        assertThat(detail.get("myVoteOptionId")).isEqualTo((int) b);
    }

    @Test
    void 存在しない投票と参加していない投票は区別できない404() {
        long host = NEXT_VISITOR.incrementAndGet();
        long outsider = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> created = body(create(host, "秘密の投票", 30, false), HttpStatus.CREATED);

        MvcTestResult notJoined = get(outsider, (String) created.get("id"));
        MvcTestResult notExist = get(outsider, "00000000-0000-4000-8000-000000000000");

        assertThat(notJoined).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
                .isEqualTo("POLL_NOT_FOUND");
        assertThat(notExist).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
                .isEqualTo("POLL_NOT_FOUND");
        assertThat(body(notJoined, HttpStatus.NOT_FOUND).get("detail"))
                .isEqualTo(body(notExist, HttpStatus.NOT_FOUND).get("detail"));
    }

    @Test
    void 締切を過ぎると投票は409_POLL_CLOSED() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> created = body(create(host, "短い投票", 5, false, "A"), HttpStatus.CREATED);
        String pollId = (String) created.get("id");
        long a = optionId(created, "A");

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertThat(put(host, "/api/polls/" + pollId + "/my-vote", "{\"optionId\":" + a + "}"))
                .as("締切の1秒前").hasStatus(HttpStatus.OK);

        clock.advance(Duration.ofSeconds(1)); // ちょうど締切時刻。締切時刻ちょうどは「締切後」
        assertThat(put(host, "/api/polls/" + pollId + "/my-vote", "{\"optionId\":" + a + "}"))
                .as("締切ちょうど").hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("POLL_CLOSED");
        assertThat(body(get(host, pollId), HttpStatus.OK)).containsEntry("status", "CLOSED");
    }

    @Test
    void 別の投票の選択肢IDで投票すると404_OPTION_NOT_FOUND() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> mine = body(create(host, "自分の投票", 30, false, "A"), HttpStatus.CREATED);
        Map<String, Object> other = body(create(host, "別の投票", 30, false, "B"), HttpStatus.CREATED);

        MvcTestResult result = put(host, "/api/polls/" + mine.get("id") + "/my-vote",
                "{\"optionId\":" + optionId(other, "B") + "}");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_NOT_FOUND");
    }

    @Test
    void 選択肢の追加は許可がなければ参加者は403_上限を超えると409() {
        long host = NEXT_VISITOR.incrementAndGet();
        long member = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> closedToMembers = body(create(host, "主催者だけ", 30, false), HttpStatus.CREATED);
        post(member, "/api/join", "{\"code\":\"" + closedToMembers.get("joinCode") + "\"}");

        assertThat(post(member, "/api/polls/" + closedToMembers.get("id") + "/options", "{\"label\":\"x\"}"))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code")
                .isEqualTo("MEMBER_OPTIONS_DISABLED");

        Map<String, Object> open = body(create(host, "10個まで", 30, false), HttpStatus.CREATED);
        for (int i = 1; i <= 10; i++) {
            assertThat(post(host, "/api/polls/" + open.get("id") + "/options", "{\"label\":\"案" + i + "\"}"))
                    .hasStatus(HttpStatus.CREATED);
        }
        assertThat(post(host, "/api/polls/" + open.get("id") + "/options", "{\"label\":\"案11\"}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_LIMIT_REACHED");
    }

    @Test
    void 参加者は50人まで_51人目は409_POLL_FULL() {
        long host = NEXT_VISITOR.incrementAndGet();
        Map<String, Object> created = body(create(host, "満員", 30, false), HttpStatus.CREATED);
        String code = (String) created.get("joinCode");

        for (int i = 0; i < 49; i++) { // 主催者を含めて50人
            assertThat(post(NEXT_VISITOR.incrementAndGet(), "/api/join", "{\"code\":\"" + code + "\"}"))
                    .hasStatus(HttpStatus.OK);
        }
        assertThat(post(NEXT_VISITOR.incrementAndGet(), "/api/join", "{\"code\":\"" + code + "\"}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("POLL_FULL");
    }

    // ---- ヘルパー ----

    private MvcTestResult create(long visitor, String title, int minutes, boolean allowMemberOptions,
            String... options) {
        StringBuilder list = new StringBuilder();
        for (String o : options) {
            list.append(list.isEmpty() ? "" : ",").append('"').append(o).append('"');
        }
        return post(visitor, "/api/polls", "{\"title\":\"" + title + "\",\"closesInMinutes\":" + minutes
                + ",\"allowMemberOptions\":" + allowMemberOptions + ",\"options\":[" + list + "]}");
    }

    private MvcTestResult post(long visitor, String uri, String body) {
        return mvc.post().uri(uri).header("X-Visitor-Id", String.valueOf(visitor))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult put(long visitor, String uri, String body) {
        return mvc.put().uri(uri).header("X-Visitor-Id", String.valueOf(visitor))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult get(long visitor, String pollId) {
        return mvc.get().uri("/api/polls/" + pollId).header("X-Visitor-Id", String.valueOf(visitor)).exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(MvcTestResult result, HttpStatus expected) {
        assertThat(result).hasStatus(expected);
        return json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static long optionId(Map<String, Object> detail, String label) {
        return ((java.util.List<Map<String, Object>>) detail.get("options")).stream()
                .filter(o -> label.equals(o.get("label")))
                .mapToLong(o -> ((Number) o.get("id")).longValue())
                .findFirst().orElseThrow();
    }
}
