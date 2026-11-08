package com.example.quickvote;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP（MockMvc）経由で、アプリ全体（Security・Jackson・ProblemDetail・DB）をつないだ動作を確かめる。
 * ここは「つながっていること」の確認。個別の条件（権限表、同時実行など）は別のテストで詳しく見る。
 */
class HttpFlowIT extends AbstractIntegrationTest {

    private Map<String, Object> createBody(String... options) {
        return Map.of("title", "昼ごはん", "closesInMinutes", 60,
                "allowMemberOptions", false, "options", List.of(options));
    }

    private String visitorSetCookie(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith("QV_VISITOR=")).findFirst().orElseThrow();
    }

    private TestVisitor visitorFrom(MvcTestResult result) {
        String header = visitorSetCookie(result);
        String token = header.substring("QV_VISITOR=".length(), header.indexOf(';'));
        return new TestVisitor(0, token);
    }

    @Test
    void Cookieなしで投票を作ると訪問者Cookieが発行され_属性が正しい() {
        MvcTestResult created = request(HttpMethod.POST, "/api/polls", null, createBody("カレー", "ラーメン"));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String setCookie = visitorSetCookie(created);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=2592000");
        assertThat(setCookie).doesNotContain("Secure"); // 開発設定（quickvote.visitor-cookie.secure=false）
        // 発行したトークンそのものは DB に保存されない。保存されるのは SHA-256 だけ。
        String token = visitorFrom(created).token();
        assertThat(jdbc.sql("SELECT count(*) FROM visitors WHERE token_hash = :t").param("t", token)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT length(token_hash) FROM visitors").query(Integer.class).single()).isEqualTo(64);
    }

    @Test
    void 作成_参加_選択肢追加_投票_締切までHTTPで一通り動く() {
        // 主催者が作る
        MvcTestResult created = request(HttpMethod.POST, "/api/polls", null, createBody("カレー", "ラーメン"));
        TestVisitor host = visitorFrom(created);
        PollDetail poll = bodyAs(created, PollDetail.class);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/polls/" + poll.id());
        assertThat(poll.role()).isEqualTo("HOST");
        assertThat(poll.joinCode()).hasSize(8);
        assertThat(poll.options()).hasSize(2);

        // 参加者が参加コードで参加する（小文字で入力しても正規化される）
        MvcTestResult joined = request(HttpMethod.POST, "/api/join", null, Map.of("code", poll.joinCode().toLowerCase()));
        assertThat(joined).hasStatusOk();
        TestVisitor member = visitorFrom(joined);

        // 参加者による選択肢の追加は、主催者が許可していなければ 403。主催者は追加できる。
        MvcTestResult denied = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", member,
                Map.of("label", "うどん"), "Idempotency-Key", "k-1");
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code")
                .isEqualTo("MEMBER_OPTIONS_DISABLED");
        MvcTestResult added = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", host,
                Map.of("label", "うどん"), "Idempotency-Key", "k-1");
        assertThat(added).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.label").isEqualTo("うどん");

        // 参加者の画面: 参加コードは見えない
        PollDetail asMember = bodyAs(request(HttpMethod.GET, "/api/polls/" + poll.id(), member, null), PollDetail.class);
        assertThat(asMember.role()).isEqualTo("MEMBER");
        assertThat(asMember.joinCode()).isNull();
        assertThat(asMember.options()).hasSize(3);
        long optionId = asMember.options().get(0).id();

        // 投票すると、主催者の画面に反映される
        assertThat(request(HttpMethod.PUT, "/api/polls/" + poll.id() + "/my-vote", member, Map.of("optionId", optionId)))
                .hasStatusOk();
        PollDetail asHost = bodyAs(request(HttpMethod.GET, "/api/polls/" + poll.id(), host, null), PollDetail.class);
        assertThat(asHost.totalVotes()).isEqualTo(1);
        assertThat(asHost.participantCount()).isEqualTo(2);
        assertThat(asHost.options().get(0).percent()).isEqualByComparingTo("100.0");
        assertThat(asHost.lastEventSeq()).isEqualTo(3); // 参加(1) → 選択肢追加(2) → 投票(3)

        // 締め切ると、投票は 409 POLL_CLOSED（ProblemDetail）
        assertThat(request(HttpMethod.POST, "/api/polls/" + poll.id() + "/close", host, null))
                .hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult afterClose = request(HttpMethod.PUT, "/api/polls/" + poll.id() + "/my-vote", member,
                Map.of("optionId", optionId));
        assertThat(afterClose).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("POLL_CLOSED");
        assertThat(afterClose.getResponse().getContentType()).startsWith("application/problem+json");
    }

    @Test
    void 訪問者を識別できないAPI呼び出しは401() {
        MvcTestResult result = request(HttpMethod.GET, "/api/polls/" + UUID.randomUUID(), null, null);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 訪問者Cookieを新規発行するのは入口のAPIだけ() {
        request(HttpMethod.GET, "/api/polls/" + UUID.randomUUID(), null, null);

        assertThat(countRows("visitors")).isZero();
    }

    @Test
    void CSRFトークンなしの更新リクエストは403() {
        TestVisitor visitor = newVisitor();

        MvcTestResult result = requestWithoutCsrf(HttpMethod.POST, "/api/polls", visitor, createBody());

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code")
                .isEqualTo("CSRF_REJECTED");
    }

    @Test
    void 壊れたJSONや不正なパスもProblemDetailで返る() {
        TestVisitor visitor = newVisitor();

        MvcTestResult broken = request(HttpMethod.POST, "/api/polls", visitor, "{broken");
        assertThat(broken).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("BAD_REQUEST");

        MvcTestResult badId = request(HttpMethod.GET, "/api/polls/not-a-uuid", visitor, null);
        assertThat(badId).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult unknown = request(HttpMethod.GET, "/api/unknown", visitor, null);
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void 巨大なリクエストボディは拒否される() {
        TestVisitor visitor = newVisitor();
        // 内容は正しいが、未知のフィールドで 20,000 文字に膨らませたJSON。
        // spring.jackson.factory.constraints.read.max-document-length（16,384）が無ければ、そのまま受理される。
        String huge = "{\"title\":\"ok\",\"closesInMinutes\":60,\"junk\":\""
                + "a".repeat(20_000) + "\"}";
        String small = "{\"title\":\"ok\",\"closesInMinutes\":60,\"junk\":\"a\"}";

        assertThat(request(HttpMethod.POST, "/api/polls", visitor, small)).hasStatus(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, "/api/polls", visitor, huge)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 検証エラーはcodeとフィールド一覧を含むProblemDetailで返り_入力値は含まない() {
        TestVisitor visitor = newVisitor();
        Map<String, Object> body = Map.of("title", "  ", "closesInMinutes", 60,
                "options", List.of("ok", ""));

        MvcTestResult result = request(HttpMethod.POST, "/api/polls", visitor, body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asList()
                .contains("title", "options[1]");
    }

    @ParameterizedTest
    @ValueSource(ints = { 4, 0, -5, 10081 })
    void 締切の分数が5分から10080分の範囲外なら400_VALIDATION_FAILED(int minutes) {
        TestVisitor visitor = newVisitor();

        MvcTestResult result = request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", "x", "closesInMinutes", minutes));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("closesInMinutes");
        assertThat(countRows("polls")).isZero();
    }

    @Test
    void 文字数はコードポイント数で数える_絵文字100個は通り_101個は拒否される() {
        // 絵文字（サロゲートペア）は UTF-16 では2文字分だが、コードポイントでは1。仕様の「100文字」はコードポイント数。
        TestVisitor visitor = newVisitor();
        String emoji100 = "😀".repeat(100); // UTF-16 で200、UTF-8 で400バイト
        String emoji101 = "😀".repeat(101);

        assertThat(request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", emoji100, "closesInMinutes", 60, "options", List.of(emoji100))))
                .hasStatus(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, "/api/polls", visitor, Map.of("title", emoji101, "closesInMinutes", 60)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 結合文字やZWJでつないだ絵文字は_見た目が1文字でも複数のコードポイントとして数える() {
        TestVisitor visitor = newVisitor();
        // 家族の絵文字 = 男 ZWJ 女 ZWJ 女の子 ZWJ 男の子 = 7コードポイント（見た目は1文字）。14個で98、15個で105。
        String family = "👨‍👩‍👧‍👦";
        assertThat(family.codePointCount(0, family.length())).isEqualTo(7);

        assertThat(request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", family.repeat(14), "closesInMinutes", 60))).hasStatus(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", family.repeat(15), "closesInMinutes", 60))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 締切の分数が省略または整数でないなら400() {
        TestVisitor visitor = newVisitor();

        MvcTestResult missing = request(HttpMethod.POST, "/api/polls", visitor, Map.of("title", "x"));
        MvcTestResult text = request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", "x", "closesInMinutes", "あとで"));

        assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("closesInMinutes");
        assertThat(text).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 画面で選べる最短の5分と最長の7日が通り_締切はサーバーの時計から計算される() {
        // 絶対時刻をクライアントから受け取ると、通信にかかった時間のぶん「5分後」が5分を割って拒否されていた。
        // 分数で受け取り、サーバーの Clock から締切を計算するので、5分は必ず通る。
        TestVisitor visitor = newVisitor();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        MvcTestResult shortest = request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", "x", "closesInMinutes", 5));
        MvcTestResult longest = request(HttpMethod.POST, "/api/polls", visitor,
                Map.of("title", "x", "closesInMinutes", 10080));

        assertThat(shortest).hasStatus(HttpStatus.CREATED);
        assertThat(Instant.parse((String) bodyAs(shortest, Map.class).get("closesAt")))
                .isEqualTo(now.plus(java.time.Duration.ofMinutes(5)));
        assertThat(longest).hasStatus(HttpStatus.CREATED);
        assertThat(Instant.parse((String) bodyAs(longest, Map.class).get("closesAt")))
                .isEqualTo(now.plus(java.time.Duration.ofDays(7)));
    }

    @Test
    void ProblemDetailのinstanceは相関IDで_パスに含まれる秘密を応答に残さない() {
        TestVisitor visitor = newVisitor();

        MvcTestResult result = request(HttpMethod.POST, "/api/invites/secret-token-xyz/accept", visitor, null);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.instance")
                .isEqualTo("urn:request-id:" + requestId);
        assertThat(result).bodyText().doesNotContain("secret-token-xyz");
    }
}
