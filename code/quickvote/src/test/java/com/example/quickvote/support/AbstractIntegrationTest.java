package com.example.quickvote.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.example.quickvote.TestcontainersConfiguration;
import com.example.quickvote.common.Tokens;
import com.example.quickvote.poll.CreatePollRequest;
import com.example.quickvote.poll.JoinService;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.poll.PollService;
import com.example.quickvote.security.VisitorAuthenticationFilter;
import com.example.quickvote.security.VisitorRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * 実PostgreSQL（Testcontainers）を使う統合テストの土台。
 * 同じ設定のテストクラスは ApplicationContext（とコンテナ）を使い回すので、2つ目以降は速い。
 * テストごとに全テーブルを空にし、時計を「今」に戻すので、テスト同士は独立している。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfig.class })
public abstract class AbstractIntegrationTest {

    /** テスト用の訪問者。cookie() を付けてリクエストすると、その訪問者として扱われる。 */
    public record TestVisitor(long id, String token) {
        public Cookie cookie() {
            return new Cookie(VisitorAuthenticationFilter.COOKIE_NAME, token);
        }
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected VisitorRepository visitorRepository;
    @Autowired
    protected PollService pollService;
    @Autowired
    protected JoinService joinService;
    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JsonMapper json;

    /**
     * このテスト専用の接続元IP。回数制限のバケットはアプリのメモリにあり、同じ ApplicationContext を使うテスト間で
     * 共有される。MockMvc の既定の接続元（127.0.0.1）のままだと、テストをまたいで IP 単位のバケットを使い合ってしまう。
     */
    protected String clientIp;

    @BeforeEach
    void resetState() {
        clientIp = uniqueIp();
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // CASCADE で、これらを参照するテーブル（選択肢・参加者・票・招待・イベント）もまとめて空になる。
        jdbc.sql("TRUNCATE polls, visitors, idempotency_records RESTART IDENTITY CASCADE").update();
    }

    protected static String uniqueIp() {
        UUID id = UUID.randomUUID();
        return "10." + (id.getMostSignificantBits() & 0xFF) + "." + ((id.getMostSignificantBits() >> 8) & 0xFF) + "."
                + ((id.getLeastSignificantBits() & 0xFE) + 1);
    }

    protected TestVisitor newVisitor() {
        String token = Tokens.newToken();
        long id = visitorRepository.create(Tokens.sha256Hex(token), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new TestVisitor(id, token);
    }

    /** host が投票を作る（サービス層を直接呼ぶ）。締切は10分後。 */
    protected PollDetail createPoll(TestVisitor host, boolean allowMemberOptions, String... optionLabels) {
        return pollService.create(host.id(), new CreatePollRequest("テスト投票", 10, allowMemberOptions,
                List.of(optionLabels)));
    }

    /** 締切までの時間（5分〜7日）を指定して投票を作る（時間を大きく進めるテスト用）。 */
    protected PollDetail createPoll(TestVisitor host, java.time.Duration closesIn, String... optionLabels) {
        return pollService.create(host.id(), new CreatePollRequest("テスト投票", (int) closesIn.toMinutes(), false,
                List.of(optionLabels)));
    }

    /** 参加コードで MEMBER として参加させる。 */
    protected void join(TestVisitor visitor, PollDetail poll) {
        joinService.join(visitor.id(), poll.joinCode());
    }

    protected TestVisitor newMember(PollDetail poll) {
        TestVisitor member = newVisitor();
        join(member, poll);
        return member;
    }

    /** 参加者数を手早く増やす（SQLで直接）。人数上限のテストの準備用。 */
    protected void addMembersDirectly(PollDetail poll, int count) {
        long pollId = jdbc.sql("SELECT id FROM polls WHERE public_id = :id").param("id", poll.id())
                .query(Long.class).single();
        for (int i = 0; i < count; i++) {
            TestVisitor visitor = newVisitor();
            jdbc.sql("INSERT INTO participants (poll_id, visitor_id, role, joined_at) VALUES (:p, :v, 'MEMBER', :now)")
                    .param("p", pollId).param("v", visitor.id())
                    .param("now", clock.instant().atOffset(java.time.ZoneOffset.UTC))
                    .update();
        }
    }

    protected long countRows(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    // ---- HTTP ヘルパー（MockMvcTester）。CSRFトークンは csrf() が正しいものを付ける ----

    protected MvcTestResult request(HttpMethod method, String uri, TestVisitor visitor, Object body,
            String... headerNameValuePairs) {
        var builder = mvc.method(method).uri(uri).with(csrf()).with(from(clientIp));
        if (visitor != null) {
            builder = builder.cookie(visitor.cookie());
        }
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        for (int i = 0; i < headerNameValuePairs.length; i += 2) {
            builder = builder.header(headerNameValuePairs[i], headerNameValuePairs[i + 1]);
        }
        return builder.exchange();
    }

    /** CSRFトークンを付けずに送る（拒否されることの確認用）。 */
    protected MvcTestResult requestWithoutCsrf(HttpMethod method, String uri, TestVisitor visitor, Object body) {
        var builder = mvc.method(method).uri(uri).with(from(clientIp));
        if (visitor != null) {
            builder = builder.cookie(visitor.cookie());
        }
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return builder.exchange();
    }

    /** 接続元IPを指定する。 */
    protected static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected String toJson(Object body) {
        return body instanceof String s ? s : json.writeValueAsString(body);
    }

    protected <T> T bodyAs(MvcTestResult result, Class<T> type) {
        try {
            return json.readValue(result.getResponse().getContentAsString(), type);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
