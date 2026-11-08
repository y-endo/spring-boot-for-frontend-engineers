package com.example.quickvote;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.common.Tokens;
import com.example.quickvote.security.RateLimiter;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * 誰でも叩ける入口（参加コードでの参加・招待の受諾・投票の作成）と、招待の発行の回数制限（IP単位・訪問者単位のトークンバケット）。
 * バケットはアプリのメモリにありテスト間で共有されるため、各テストは固有のIPと訪問者を使う。
 * 設定の既定値: IP は容量100・毎分60回補充、訪問者は容量10・毎分10回補充（6秒で1回）。
 * 投票・招待の作成は訪問者ごとに容量10・毎分10回補充。
 */
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired
    RateLimiter rateLimiter;

    private MvcTestResult join(String ip, TestVisitor visitor, String code) {
        return post("/api/join", ip, visitor, Map.of("code", code));
    }

    /** パスは URI のまま MockMvc に渡す（"%6a" のようなエンコードを、テンプレートの展開で変えられないように）。 */
    private MvcTestResult post(String rawPath, String ip, TestVisitor visitor, Object body) {
        var builder = mvc.post().uri(URI.create(rawPath)).with(csrf()).with(from(ip));
        if (visitor != null) {
            builder = builder.cookie(visitor.cookie());
        }
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return builder.exchange();
    }

    private static final Map<String, Object> NEW_POLL = Map.of("title", "x", "closesInMinutes", 60);

    @Test
    void 同じ訪問者は容量の10回まで試せて_11回目は429とRetry_After() {
        String ip = uniqueIp();
        TestVisitor visitor = newVisitor();

        for (int i = 0; i < 10; i++) {
            // 存在しないコードを試す総当たり。拒否される前は 404 JOIN_CODE_NOT_FOUND
            assertThat(join(ip, visitor, "ABCDEFGH")).hasStatus(HttpStatus.NOT_FOUND);
        }
        MvcTestResult limited = join(ip, visitor, "ABCDEFGH");

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().extractingPath("$.code")
                .isEqualTo("RATE_LIMITED");
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("6"); // 次の1トークンまで6秒
    }

    @Test
    void 制限中でも正しい参加コードは通らない_試行そのものが数えられる() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        String ip = uniqueIp();
        TestVisitor attacker = newVisitor();
        for (int i = 0; i < 10; i++) {
            join(ip, attacker, "ABCDEFGH");
        }

        assertThat(join(ip, attacker, poll.joinCode())).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void 時間が経つとトークンが補充されて再び試せる() {
        String ip = uniqueIp();
        TestVisitor visitor = newVisitor();
        for (int i = 0; i < 10; i++) {
            join(ip, visitor, "ABCDEFGH");
        }
        assertThat(join(ip, visitor, "ABCDEFGH")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        clock.advance(Duration.ofSeconds(6));

        assertThat(join(ip, visitor, "ABCDEFGH")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 制限は訪問者ごと_別の訪問者は影響を受けない() {
        String ip = uniqueIp();
        TestVisitor noisy = newVisitor();
        TestVisitor other = newVisitor();
        for (int i = 0; i < 11; i++) {
            join(ip, noisy, "ABCDEFGH");
        }

        assertThat(join(ip, noisy, "ABCDEFGH")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(join(ip, other, "ABCDEFGH")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void Cookieを捨てて訪問者を変え続けてもIP単位の制限で止まり_訪問者の行も増えない() {
        String ip = uniqueIp();

        // Cookie なしで連打する攻撃者。毎回新しい訪問者になれるが、IP のバケット（容量100）は共通。
        for (int i = 0; i < 100; i++) {
            assertThat(join(ip, null, "ABCDEFGH")).hasStatus(HttpStatus.NOT_FOUND);
        }
        long visitorsBefore = countRows("visitors");
        MvcTestResult limited = join(ip, null, "ABCDEFGH");

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        // 拒否はフィルタの手前で行われるので、訪問者の行が作られない（DBを荒らされない）
        assertThat(countRows("visitors")).isEqualTo(visitorsBefore);
        assertThat(limited.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void パーセントエンコードしたURLでも_参加コードの試行回数は同じ制限を受ける() {
        // Spring MVC は "/api/%6aoin" を "/api/join" として処理する（%6a は "j"）。
        // 生のURI文字列を "/api/join" と比べる実装だと、これで制限をすり抜けられた。
        String ip = uniqueIp();
        TestVisitor visitor = newVisitor();

        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/%6aoin", ip, visitor, Map.of("code", "ABCDEFGH"))).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("JOIN_CODE_NOT_FOUND"); // /api/join として処理された
        }
        MvcTestResult limited = post("/api/%6aoin", ip, visitor, Map.of("code", "ABCDEFGH"));

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        // 通常の表記と、同じバケットを共有している（エンコードを変えて回数を増やせない）
        assertThat(join(ip, visitor, "ABCDEFGH")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void 不正な招待の受諾を_Cookieを捨てて連打しても_IP単位で止まり訪問者の行は増え続けない() {
        String ip = uniqueIp();

        // 入口で訪問者の行は検証より前に作られる。404 になる受諾でも毎回1行増える。IP単位の制限が、その数を抑える。
        for (int i = 0; i < 100; i++) {
            assertThat(post("/api/invites/invalid/accept", ip, null, null)).hasStatus(HttpStatus.NOT_FOUND);
        }
        long visitorsAtLimit = countRows("visitors");
        for (int i = 0; i < 20; i++) {
            MvcTestResult limited = post("/api/invites/invalid/accept", ip, null, null);
            assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(limited.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }

        assertThat(countRows("visitors")).isEqualTo(visitorsAtLimit);
    }

    @Test
    void 検証エラーになる投票作成を_Cookieを捨てて連打しても_IP単位で止まり訪問者の行は増え続けない() {
        String ip = uniqueIp();
        Map<String, Object> invalid = Map.of("title", "  ", "closesInMinutes", 60);

        for (int i = 0; i < 100; i++) {
            assertThat(post("/api/polls", ip, null, invalid)).hasStatus(HttpStatus.BAD_REQUEST);
        }
        long visitorsAtLimit = countRows("visitors");
        for (int i = 0; i < 20; i++) {
            assertThat(post("/api/polls", ip, null, invalid)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        }

        assertThat(countRows("visitors")).isEqualTo(visitorsAtLimit);
        assertThat(countRows("polls")).isZero();
    }

    @Test
    void 招待の受諾も_訪問者単位で試行回数が制限される() {
        String ip = uniqueIp();
        TestVisitor visitor = newVisitor();

        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/invites/invalid/accept", ip, visitor, null)).hasStatus(HttpStatus.NOT_FOUND);
        }
        MvcTestResult limited = post("/api/invites/invalid/accept", ip, visitor, null);

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("6");
    }

    @Test
    void 投票の作成は_訪問者ごとに容量10回まで_11回目は429() {
        String ip = uniqueIp();
        TestVisitor visitor = newVisitor();

        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/polls", ip, visitor, NEW_POLL)).hasStatus(HttpStatus.CREATED);
        }
        MvcTestResult limited = post("/api/polls", ip, visitor, NEW_POLL);

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().extractingPath("$.code")
                .isEqualTo("RATE_LIMITED");
        assertThat(countRows("polls")).isEqualTo(10);
        // 別の訪問者は影響を受けない（同じIPでも、IP単位の容量にはまだ余裕がある）
        assertThat(post("/api/polls", ip, newVisitor(), NEW_POLL)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void 招待の発行も_訪問者ごとに容量10回まで_11回目は429() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");
        String ip = uniqueIp();

        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/polls/" + poll.id() + "/invites", ip, host, null)).hasStatus(HttpStatus.CREATED);
        }

        assertThat(post("/api/polls/" + poll.id() + "/invites", ip, host, null))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(countRows("invites")).isEqualTo(10);
    }

    @Test
    void IP単位で拒否されたら_出まかせのCookieごとの訪問者バケットは作られない() {
        String ip = uniqueIp();
        for (int i = 0; i < 100; i++) {
            post("/api/join", ip, null, Map.of("code", "ABCDEFGH")); // Cookie なし: IP のバケットだけ減らす
        }
        int bucketsBefore = rateLimiter.visitorBucketCount();

        for (int i = 0; i < 50; i++) {
            TestVisitor forged = new TestVisitor(0, Tokens.newToken()); // DB にない、形式だけ正しい Cookie 値
            assertThat(join(ip, forged, "ABCDEFGH")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        }

        assertThat(rateLimiter.visitorBucketCount()).isEqualTo(bucketsBefore);
    }

    @Test
    void 形式が違うCookieの値では_訪問者バケットを作らない() {
        String ip = uniqueIp();
        int bucketsBefore = rateLimiter.visitorBucketCount();

        for (int i = 0; i < 30; i++) {
            TestVisitor malformed = new TestVisitor(0, "x".repeat(10 + i) + "!"); // 43文字のBase64URLではない
            assertThat(join(ip, malformed, "ABCDEFGH")).hasStatus(HttpStatus.NOT_FOUND);
        }

        assertThat(rateLimiter.visitorBucketCount()).isEqualTo(bucketsBefore);
    }

    @Test
    void 制限の対象は_回数を数える入口だけ() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false, "A");

        for (int i = 0; i < 30; i++) {
            assertThat(request(HttpMethod.GET, "/api/polls/" + poll.id(), host, null)).hasStatusOk();
        }
    }
}
