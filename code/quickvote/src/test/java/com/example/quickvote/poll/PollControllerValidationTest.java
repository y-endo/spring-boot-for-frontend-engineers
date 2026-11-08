package com.example.quickvote.poll;

import java.util.List;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.IdempotencyService;
import com.example.quickvote.security.VisitorAuthentication;
import com.example.quickvote.security.VisitorPrincipal;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Web層だけを起動する軽いテスト（@WebMvcTest）。DB は使わず、PollService は偽物（@MockitoBean）に差し替える。
 * 確かめるのは「入力検証エラーが ProblemDetail の決まった形で返ること」と「業務エラーが code つきで返ること」。
 * 認証は VisitorAuthentication を直接渡し（本物のフィルタは使わない）、CSRF は csrf() で通す。
 */
@WebMvcTest(PollController.class)
class PollControllerValidationTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PollService pollService;

    private static final int VALID_CLOSES_IN_MINUTES = 60;

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri)
                .with(authentication(new VisitorAuthentication(new VisitorPrincipal(1L))))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void タイトルが空白だけなら400_VALIDATION_FAILED_で項目名つき() {
        var result = post("/api/polls", "{\"title\":\"   \",\"closesInMinutes\":" + VALID_CLOSES_IN_MINUTES + "}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isNotNull();
        verifyNoInteractions(pollService); // 検証エラーならサービスは呼ばれない
    }

    @Test
    void 絵文字だけのタイトルは100文字まで通り_101文字は弾かれる() {
        String ok = "😀".repeat(100);
        String ng = "😀".repeat(101);
        when(pollService.create(anyLong(), any())).thenThrow(new ApiException(ErrorCode.INTERNAL_ERROR));

        // 通った場合はサービスまで到達する（ここでは到達の証拠としてモックの例外=500を使う）
        assertThat(post("/api/polls", json(ok, List.of())).exchange()).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(post("/api/polls", json(ng, List.of())).exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 初期の選択肢は11個以上や空文字だと400() {
        List<String> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "o" + i).toList();

        var tooMany = post("/api/polls", json("ok", eleven)).exchange();
        var blank = post("/api/polls", json("ok", List.of("A", ""))).exchange();

        assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("options");
        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("options[1]");
    }

    @Test
    void 締切が欠けていれば400() {
        var result = post("/api/polls", "{\"title\":\"x\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("closesInMinutes");
    }

    @Test
    void 検証エラーの応答には入力値を含めない() {
        var result = post("/api/polls", "{\"title\":\"" + "秘密".repeat(60) + "\",\"closesInMinutes\":"
                + VALID_CLOSES_IN_MINUTES + "}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyText().doesNotContain("秘密");
    }

    @Test
    void 締切の分数が範囲外なら400で_サービスを呼ばない() {
        var result = post("/api/polls", "{\"title\":\"ok\",\"closesInMinutes\":4}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("closesInMinutes");
        verifyNoInteractions(pollService);
    }

    @Test
    void 業務エラーはcodeつきのProblemDetailで返る() {
        when(pollService.create(anyLong(), any())).thenThrow(new ApiException(ErrorCode.RATE_LIMITED));

        var result = post("/api/polls", json("ok", List.of())).exchange();

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
        assertThat(result).bodyJson().extractingPath("$.detail").isNotNull();
    }

    @Test
    void POLL_FULLは409でcodeを持つ() {
        when(pollService.addOption(anyLong(), any(), any(), any()))
                .thenThrow(new ApiException(ErrorCode.OPTION_LIMIT_REACHED));

        var result = post("/api/polls/3f2504e0-4f89-41d3-9a0c-0305e82c3301/options", "{\"label\":\"x\"}")
                .header("Idempotency-Key", "k-1").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_LIMIT_REACHED");
    }

    @Test
    void 選択肢追加はIdempotency_Keyがなければ400で_サービスを呼ばない() {
        var result = post("/api/polls/3f2504e0-4f89-41d3-9a0c-0305e82c3301/options", "{\"label\":\"x\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(pollService);
    }

    @Test
    void 再送で返した応答にはIdempotent_Replayedヘッダーが付く() {
        when(pollService.addOption(anyLong(), any(), any(), any())).thenReturn(
                new IdempotencyService.Result<>(201, new OptionResponse(5, "x"), true));

        var result = post("/api/polls/3f2504e0-4f89-41d3-9a0c-0305e82c3301/options", "{\"label\":\"x\"}")
                .header("Idempotency-Key", "k-1").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(5);
    }

    private static String json(String title, List<String> options) {
        String optionsJson = options.stream().map(o -> "\"" + o + "\"").collect(java.util.stream.Collectors.joining(","));
        return "{\"title\":\"" + title + "\",\"closesInMinutes\":" + VALID_CLOSES_IN_MINUTES + ",\"options\":["
                + optionsJson + "]}";
    }
}
