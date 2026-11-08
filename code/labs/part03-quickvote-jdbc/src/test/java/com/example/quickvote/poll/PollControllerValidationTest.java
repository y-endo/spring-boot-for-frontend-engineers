package com.example.quickvote.poll;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.QuickVoteProperties;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Web層だけを起動する軽いテスト（@WebMvcTest）。PollService は偽物（@MockitoBean）に差し替えるので、Map も使わない。
 * 確かめるのは「入力検証エラーが ProblemDetail の決まった形で返ること」と「業務エラーが code つきで返ること」。
 * 訪問者の特定は暫定のヘッダー（X-Visitor-Id）で行う。
 */
@WebMvcTest(PollController.class)
@EnableConfigurationProperties(QuickVoteProperties.class) // スライスには設定クラスが含まれないので、必要なものを足す
class PollControllerValidationTest {

    private static final int VALID_MINUTES = 30;
    private static final String POLL_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PollService pollService;

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri)
                .header("X-Visitor-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void タイトルが空白だけなら400_VALIDATION_FAILED_で項目名つき() {
        var result = post("/api/polls", "{\"title\":\"   \",\"closesInMinutes\":" + VALID_MINUTES + "}").exchange();

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
        when(pollService.create(anyLong(), any())).thenThrow(new ApiException(ErrorCode.INTERNAL_ERROR));

        // 通った場合はサービスまで到達する（ここでは到達の証拠としてモックの例外=500を使う）
        assertThat(post("/api/polls", json("😀".repeat(100), List.of())).exchange())
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(post("/api/polls", json("😀".repeat(101), List.of())).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 初期の選択肢は11個以上や空文字だと400() {
        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "o" + i).toList();

        var tooMany = post("/api/polls", json("ok", eleven)).exchange();
        var blank = post("/api/polls", json("ok", List.of("A", ""))).exchange();

        assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("options");
        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                .isEqualTo("options[1]");
    }

    @Test
    void 締切が欠けている_または範囲外なら400() {
        var missing = post("/api/polls", "{\"title\":\"x\"}").exchange();
        var tooSoon = post("/api/polls", "{\"title\":\"x\",\"closesInMinutes\":4}").exchange();
        var tooLate = post("/api/polls", "{\"title\":\"x\",\"closesInMinutes\":10081}").exchange();

        for (var result : List.of(missing, tooSoon, tooLate)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field")
                    .isEqualTo("closesInMinutes");
        }
    }

    @Test
    void 検証エラーの応答には入力値を含めない() {
        var result = post("/api/polls", "{\"title\":\"" + "秘密".repeat(60) + "\",\"closesInMinutes\":" + VALID_MINUTES
                + "}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyText().doesNotContain("秘密");
    }

    @Test
    void 業務エラーはcodeつきのProblemDetailで返る() {
        when(pollService.get(anyLong(), any(UUID.class))).thenThrow(new ApiException(ErrorCode.POLL_NOT_FOUND));

        var result = mvc.get().uri("/api/polls/" + POLL_ID).header("X-Visitor-Id", "1").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail").isNotNull();
    }

    @Test
    void 選択肢が上限なら409_OPTION_LIMIT_REACHED() {
        when(pollService.addOption(anyLong(), any(UUID.class), any()))
                .thenThrow(new ApiException(ErrorCode.OPTION_LIMIT_REACHED));

        var result = post("/api/polls/" + POLL_ID + "/options", "{\"label\":\"x\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("OPTION_LIMIT_REACHED");
    }

    @Test
    void 訪問者ヘッダーがなければ401_UNAUTHENTICATED_でサービスを呼ばない() {
        var result = mvc.get().uri("/api/polls/" + POLL_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        verifyNoInteractions(pollService);
    }

    @Test
    void 壊れたJSONは400でcodeはステータス名() {
        var result = post("/api/polls", "{\"title\":").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void 想定外の例外は500でスタックトレースを含めない() {
        when(pollService.create(anyLong(), any())).thenThrow(new IllegalStateException("内部の事情 secret-detail"));

        var result = post("/api/polls", json("ok", List.of())).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("secret-detail").doesNotContain("IllegalStateException")
                .doesNotContain("at com.example");
    }

    @Test
    void UNIQUE違反は409_ALREADY_EXISTSで_重複した値は応答に含めない() {
        when(pollService.create(anyLong(), any()))
                .thenThrow(new DuplicateKeyException("Key (join_code)=(SECRET99) already exists"));

        var result = post("/api/polls", json("ok", List.of())).exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("ALREADY_EXISTS");
        assertThat(result).bodyText().doesNotContain("SECRET99").doesNotContain("join_code");
    }

    private static String json(String title, List<String> options) {
        String optionsJson = options.stream().map(o -> "\"" + o + "\"").collect(Collectors.joining(","));
        return "{\"title\":\"" + title + "\",\"closesInMinutes\":" + VALID_MINUTES + ",\"options\":[" + optionsJson + "]}";
    }
}
