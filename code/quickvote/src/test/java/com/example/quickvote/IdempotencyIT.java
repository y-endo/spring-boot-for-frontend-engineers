package com.example.quickvote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.IdempotencyService;
import com.example.quickvote.poll.OptionResponse;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.support.Concurrently;
import com.example.quickvote.support.Concurrently.Outcome;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 選択肢追加（POST /options）の冪等性。「応答が消えても、再送して二重登録しない」ことを確かめる。 */
class IdempotencyIT extends AbstractIntegrationTest {

    private MvcTestResult addOption(TestVisitor visitor, PollDetail poll, String label, String key) {
        return request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", visitor, Map.of("label", label),
                "Idempotency-Key", key);
    }

    @Test
    void 同じキーを逐次再送すると_同じ応答が返り_登録は1件だけ() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);

        MvcTestResult first = addOption(host, poll, "うどん", "key-1");
        // クライアントが応答を受け取れず、同じリクエストを再送した状況
        MvcTestResult retry = addOption(host, poll, "うどん", "key-1");

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(bodyAs(retry, OptionResponse.class)).isEqualTo(bodyAs(first, OptionResponse.class));
        assertThat(countRows("options")).isEqualTo(1);
        // 再送ではイベントも増えない
        assertThat(countRows("poll_events")).isEqualTo(1);
    }

    @Test
    void 同じキーを同時に10回送っても_登録は1件で_全員が同じ応答を受け取る() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);

        List<Outcome<IdempotencyService.Result<OptionResponse>>> outcomes = Concurrently.run(10,
                i -> () -> pollService.addOption(host.id(), poll.id(), "same-key", "うどん"));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).filteredOn(o -> !o.value().replayed()).hasSize(1); // 実際に実行したのは1回だけ
        assertThat(outcomes.stream().map(o -> o.value().body().id()).distinct()).hasSize(1);
        assertThat(countRows("options")).isEqualTo(1);
        assertThat(countRows("idempotency_records")).isEqualTo(1);
    }

    @Test
    void 同じキーで本文が違えば422() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);
        addOption(host, poll, "うどん", "key-1");

        MvcTestResult reused = addOption(host, poll, "そば", "key-1");

        assertThat(reused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.code")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(countRows("options")).isEqualTo(1);
    }

    @Test
    void 同じキーを別の投票に使い回しても422() {
        TestVisitor host = newVisitor();
        PollDetail pollA = createPoll(host, false);
        PollDetail pollB = createPoll(host, false);
        addOption(host, pollA, "うどん", "key-1");

        MvcTestResult reused = addOption(host, pollB, "うどん", "key-1");

        assertThat(reused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void キーの範囲は訪問者ごと_別の人が同じキーを使っても干渉しない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, true);
        TestVisitor member = newMember(poll);

        MvcTestResult byHost = addOption(host, poll, "うどん", "shared-key");
        MvcTestResult byMember = addOption(member, poll, "そば", "shared-key");

        assertThat(byHost).hasStatus(HttpStatus.CREATED);
        assertThat(byMember).hasStatus(HttpStatus.CREATED);
        assertThat(countRows("options")).isEqualTo(2);
    }

    @Test
    void Idempotency_Keyがない_または形式が不正なら400() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);

        MvcTestResult missing = request(HttpMethod.POST, "/api/polls/" + poll.id() + "/options", host,
                Map.of("label", "うどん"));
        MvcTestResult blank = addOption(host, poll, "うどん", "has space");
        MvcTestResult tooLong = addOption(host, poll, "うどん", "k".repeat(201));

        for (MvcTestResult result : List.of(missing, blank, tooLong)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                    .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        }
        assertThat(countRows("options")).isZero();
    }

    @Test
    void 保持期限の24時間以内なら再送は保存済みの応答で_過ぎたら新しいリクエストとして扱う() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, Duration.ofDays(3)); // 時間を進めても締切にならない長さ
        MvcTestResult first = addOption(host, poll, "うどん", "key-1");

        clock.advance(Duration.ofHours(23));
        MvcTestResult within = addOption(host, poll, "うどん", "key-1");
        assertThat(bodyAs(within, OptionResponse.class).id()).isEqualTo(bodyAs(first, OptionResponse.class).id());
        assertThat(countRows("options")).isEqualTo(1);

        // 23時間 + 2時間 = 25時間後: 保持期限(24時間)を過ぎている。定期削除が動く前でも、キーは再利用できる。
        clock.advance(Duration.ofHours(2));
        MvcTestResult after = addOption(host, poll, "うどん", "key-1");
        assertThat(after.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(countRows("options")).isEqualTo(2);
    }

    @Test
    void 失敗した試行は記録されず_同じキーで再送すれば再実行される() {
        TestVisitor host = newVisitor();
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("選択肢" + i);
        }
        PollDetail poll = createPoll(host, false, ten.toArray(new String[0]));

        MvcTestResult full = addOption(host, poll, "11個目", "key-1");

        assertThat(full).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_LIMIT_REACHED");
        assertThat(countRows("idempotency_records")).isZero();
    }

    @Test
    void 参加していない人は冪等性の記録も作れない() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);
        TestVisitor outsider = newVisitor();

        Throwable thrown = catchThrowable(() -> pollService.addOption(outsider.id(), poll.id(), "key-1", "うどん"));

        assertThat(thrown).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.POLL_NOT_FOUND));
        assertThat(countRows("idempotency_records")).isZero();
    }
}
