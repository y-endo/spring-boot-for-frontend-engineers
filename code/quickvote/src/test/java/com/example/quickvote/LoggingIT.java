package com.example.quickvote;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.example.quickvote.poll.CreatePollRequest;
import com.example.quickvote.poll.ExpiredDataCleaner;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ログに「出してはいけないもの」が出ていないこと、出すべきもの（相関ID）が出ていることを確かめる。
 * 方針は「許可した項目だけを出す」。リクエストの本文・URL・ヘッダー・Cookie は出さない。
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingIT extends AbstractIntegrationTest {

    private static final String SECRET_LABEL = "秘密のラベル-7f3a9c";
    private static final String SECRET_TITLE = "秘密のタイトル-4d5e6f";

    @Autowired
    ExpiredDataCleaner cleaner;

    @Test
    void 選択肢ラベル_参加コード_招待トークン_訪問者トークンはログに出ない(CapturedOutput output) {
        // 新規の訪問者として作成 → 参加 → 選択肢追加 → 招待発行 → 受諾、までを HTTP で通す
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true, SECRET_LABEL);
        TestVisitor member = newVisitor();
        request(HttpMethod.POST, "/api/join", member, Map.of("code", poll.joinCode()));
        request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", member, Map.of("label", SECRET_LABEL + "-2"),
                "Idempotency-Key", "key-logging");
        String inviteToken = bodyAs(request(HttpMethod.POST, "/api/polls/" + poll.id() + "/invites", host, null),
                Map.class).get("token").toString();
        TestVisitor invitee = newVisitor();
        request(HttpMethod.POST, "/api/invites/" + inviteToken + "/accept", invitee, null);
        // 失敗するリクエスト（検証エラー・存在しない参加コード）も通す
        request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", host, Map.of("label", ""),
                "Idempotency-Key", "key-bad");
        request(HttpMethod.POST, "/api/join", member, Map.of("code", "ZZZZZZZZ"));

        String logs = output.getAll();

        assertThat(logs).contains("request completed"); // アクセスログ相当は出ている
        assertThat(logs).doesNotContain(SECRET_LABEL);
        assertThat(logs).doesNotContain(poll.joinCode());
        assertThat(logs).doesNotContain(inviteToken);
        assertThat(logs).doesNotContain(host.token()).doesNotContain(member.token()).doesNotContain(invitee.token());
        assertThat(logs).doesNotContain("key-logging"); // Idempotency-Key も出さない
        // URLの代わりにルートのパターンが出る（IDやトークンは含まれない）
        assertThat(logs).contains("/api/invites/{token}/accept").contains("/api/polls/{pollId}/options");
    }

    @Test
    void リクエストごとに相関IDが付き_ログと応答ヘッダーで一致する(CapturedOutput output) {
        TestVisitor host = newVisitor();

        MvcTestResult result = request(HttpMethod.GET, "/api/polls/00000000-0000-0000-0000-000000000000", host, null);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(output.getAll()).contains("\"requestId\":\"" + requestId + "\"");
    }

    @Test
    void 呼び出し側が渡した相関IDは引き継がれ_不正な形式は捨てて採番し直す(CapturedOutput output) {
        TestVisitor host = newVisitor();

        MvcTestResult passed = request(HttpMethod.GET, "/api/unknown", host, null, "X-Request-Id", "trace-abc.123");
        MvcTestResult rejected = request(HttpMethod.GET, "/api/unknown", host, null, "X-Request-Id",
                "bad id\nforged-log-line");

        assertThat(passed.getResponse().getHeader("X-Request-Id")).isEqualTo("trace-abc.123");
        assertThat(rejected.getResponse().getHeader("X-Request-Id")).isNotEqualTo("bad id\nforged-log-line")
                .matches("[0-9a-f-]{36}"); // 改行入りの値は信用せず、UUIDを採番する
        assertThat(output.getAll()).doesNotContain("forged-log-line");
    }

    @Test
    void 想定外の例外は応答にスタックトレースを出さず_ログに1回だけ出る(CapturedOutput output) {
        TestVisitor host = newVisitor();
        // DB の整合性が壊れている状況の再現: 参加者表を直接壊して、サービスに想定外の例外を起こさせる
        PollDetail poll = createPoll(host, false, "A");
        jdbc.sql("ALTER TABLE poll_events RENAME TO poll_events_renamed").update();
        try {
            MvcTestResult result = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/close", host, null);

            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR).bodyJson().extractingPath("$.code")
                    .isEqualTo("INTERNAL_ERROR");
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("Exception").doesNotContain("at com.example").doesNotContain("poll_events");
            assertThat(output.getAll()).contains("想定外のエラーが発生しました");
            assertThat(countOccurrences(output.getAll(), "想定外のエラーが発生しました")).isEqualTo(1);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            jdbc.sql("ALTER TABLE poll_events_renamed RENAME TO poll_events").update();
        }
    }

    @Test
    void DBの制約違反の例外に含まれる入力値_タイトルや参加コード_は_ログに出ない(CapturedOutput output) {
        TestVisitor host = newVisitor();
        PollDetail poll = pollService.create(host.id(),
                new CreatePollRequest(SECRET_TITLE, 10, false, List.of()));
        // 時計がずれた複数インスタンスでも起こりうる状況の再現: 作成時刻より前の時刻で手動締切する。
        // closes_at を「今」に前倒しする UPDATE が CHECK (closes_at >= created_at) に違反する。
        clock.set(clock.instant().minus(Duration.ofHours(1)));

        // 前提の確認: この DB エラーのメッセージには、実際に違反した行の中身（タイトル・参加コード）が含まれる。
        Throwable dbError = catchThrowable(() -> jdbc.sql("UPDATE polls SET closes_at = created_at - interval '1 hour'")
                .update());
        assertThat(dbError).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(dbError.getCause().getMessage()).contains(SECRET_TITLE, poll.joinCode());

        MvcTestResult result = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/close", host, null);

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR).bodyJson().extractingPath("$.code")
                .isEqualTo("INTERNAL_ERROR");
        String logs = output.getAll();
        assertThat(logs).contains("想定外のエラーが発生しました");
        // 例外の型と SQLSTATE（23514 = CHECK 制約違反）、スタックのフレームは残る。メッセージ（行の中身）は出ない。
        assertThat(logs).contains("SQLSTATE=23514").contains("DataIntegrityViolationException");
        assertThat(logs).doesNotContain(SECRET_TITLE).doesNotContain(poll.joinCode()).doesNotContain("Failing row");
    }

    @Test
    void 期限切れ削除が_DBエラーで失敗しても_エラーメッセージの内容はログに出ない(CapturedOutput output) {
        TestVisitor host = newVisitor();
        createPoll(host, false, "A");
        // 削除のたびに、秘密の文字列を含むメッセージで失敗するトリガー（DB の例外メッセージに値が入る状況の再現）
        jdbc.sql("""
                CREATE FUNCTION fail_on_delete() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'secret-%', 'trigger-7f3a9c'; END;
                $$ LANGUAGE plpgsql
                """).update();
        jdbc.sql("CREATE TRIGGER fail_on_delete BEFORE DELETE ON polls FOR EACH ROW EXECUTE FUNCTION fail_on_delete()")
                .update();
        try {
            clock.advance(Duration.ofDays(8)); // 作成から7日を過ぎ、削除の対象になる

            cleaner.scheduledRun();

            String logs = output.getAll();
            assertThat(logs).contains("期限切れデータの削除に失敗しました");
            assertThat(logs).doesNotContain("secret-trigger-7f3a9c");
        } finally {
            jdbc.sql("DROP TRIGGER fail_on_delete ON polls").update();
            jdbc.sql("DROP FUNCTION fail_on_delete()").update();
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
