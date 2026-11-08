package com.example.quickvote.journey;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.quickvote.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第12章: 実際の Tomcat にリクエストを1本送り、通過ログ（journey プロファイル）の順番とスレッドを確かめる。
 * 「入る順」と「戻る順」、そして最初から最後まで同じスレッドが運ぶこと。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("journey")
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class JourneyLogIT {

    private static final Pattern STEP = Pattern.compile("\\[journey\\] (.+?) \\| (.*) \\| thread=(\\S+)");

    @LocalServerPort
    int port;

    @Test
    void リクエスト1つは_Filter_Interceptor_Jackson_Controller_Service_Repositoryの順に通り_同じスレッドが運ぶ(
            CapturedOutput output) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/polls"))
                        .header("X-Visitor-Id", "4242")
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"title\":\"journey\",\"closesInMinutes\":30,\"options\":[\"A\"]}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        // アクセスログ（RequestIdFilter）は、レスポンスを返す直前に出る。出揃うのを待つ。
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> output.getOut().contains("request completed"));

        List<Step> steps = output.getOut().lines().map(STEP::matcher).filter(Matcher::find)
                .map(m -> new Step(m.group(1).strip(), m.group(2), m.group(3))).toList();
        steps.forEach(s -> System.out.println("[demo] " + s.stage() + " | " + s.detail() + " | " + s.thread()));

        // 通過の順番
        List<String> order = steps.stream().map(s -> s.stage() + " " + s.detail()).toList();
        assertThat(indexOf(order, "Filter 入る")).isLessThan(indexOf(order, "Interceptor 前"));
        assertThat(indexOf(order, "Interceptor 前")).isLessThan(indexOf(order, "Jackson 読む"));
        assertThat(indexOf(order, "Jackson 読む")).isLessThan(indexOf(order, "呼び出し PollController.create"));
        assertThat(indexOf(order, "呼び出し PollController.create"))
                .isLessThan(indexOf(order, "呼び出し   PollService.create"));
        assertThat(indexOf(order, "呼び出し   PollService.create"))
                .isLessThan(indexOf(order, "呼び出し     PollRepository.insert"));
        assertThat(indexOf(order, "呼び出し     PollRepository.insert")).isLessThan(indexOf(order, "Jackson 書く"));
        assertThat(indexOf(order, "Jackson 書く")).isLessThan(indexOf(order, "Interceptor 後"));
        assertThat(indexOf(order, "Interceptor 後")).isLessThan(indexOf(order, "Filter 戻る"));

        // 最初から最後まで、同じ1つのスレッド（Tomcat のスレッドプールの1本）が運ぶ
        assertThat(steps.stream().map(Step::thread).distinct().toList()).hasSize(1);
        assertThat(steps.getFirst().thread()).contains("exec");
    }

    private static int indexOf(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("通過ログに見つかりません: " + prefix + "\n" + String.join("\n", lines));
    }

    private record Step(String stage, String detail, String thread) {
    }
}
