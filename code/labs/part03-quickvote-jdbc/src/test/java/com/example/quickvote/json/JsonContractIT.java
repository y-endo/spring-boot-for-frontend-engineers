package com.example.quickvote.json;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.quickvote.TestcontainersConfiguration;
import com.example.quickvote.poll.CreatePollRequest;
import com.example.quickvote.poll.Poll;
import com.example.quickvote.poll.PollStatus;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON と Java の境界で何が起きるかを、Jackson 3 で実際に確かめる（第13章）。
 * Spring Boot が構成した JsonMapper（自動構成）と、素の JsonMapper の両方で比べる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JsonContractIT {

    @Autowired
    JsonMapper boot; // 自動構成された Bean（コンストラクタ注入と同じ仕組みで、テストではフィールドに注入）

    private final JsonMapper bare = JsonMapper.builder().build();

    record Plain(String title, int closesInMinutes) {
    }

    record WithPrimitive(String title, boolean allowMemberOptions) {
    }

    @Test
    void 未知のフィールドは黙って無視される() {
        String json = "{\"title\":\"t\",\"closesInMinutes\":30,\"extra\":1}";

        assertThat(bare.readValue(json, Plain.class)).isEqualTo(new Plain("t", 30));
        assertThat(boot.readValue(json, Plain.class)).isEqualTo(new Plain("t", 30));
    }

    @Test
    void 型が違っても_読み替えられてしまう() {
        // 文字列のはずの title に数値、数値のはずの closesInMinutes に文字列や小数、真偽値に 1 や "true"
        CreatePollRequest r = boot.readValue(
                "{\"title\":123,\"closesInMinutes\":\"30\",\"allowMemberOptions\":1}", CreatePollRequest.class);
        assertThat(r.title()).isEqualTo("123");
        assertThat(r.closesInMinutes()).isEqualTo(30);
        assertThat(r.allowMemberOptions()).isTrue();

        // 章の冒頭の例：型も項目もめちゃくちゃなJSONが、例外なしで読めてしまう
        CreatePollRequest messy = boot.readValue(
                "{\"title\":123,\"closesInMinutes\":\"30\",\"allowMemberOptions\":1,\"extra\":\"hello\"}",
                CreatePollRequest.class);
        assertThat(messy.title()).isEqualTo("123");

        // 小数は黙って切り捨てられる
        assertThat(boot.readValue("{\"title\":\"t\",\"closesInMinutes\":30.7}", CreatePollRequest.class)
                .closesInMinutes()).isEqualTo(30);
        assertThat(boot.readValue("{\"title\":\"t\",\"closesInMinutes\":30,\"allowMemberOptions\":\"true\"}",
                CreatePollRequest.class).allowMemberOptions()).isTrue();
    }

    @Test
    void 読み替えられない値は例外になる() {
        assertThatThrownBy(() -> boot.readValue("{\"title\":\"t\",\"closesInMinutes\":\"abc\"}", CreatePollRequest.class))
                .isInstanceOf(InvalidFormatException.class);
        assertThatThrownBy(() -> boot.readValue("[1]", CreatePollRequest.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void プリミティブ型の項目は_欠落もnullも例外になる() {
        assertThatThrownBy(() -> boot.readValue("{\"title\":\"t\"}", WithPrimitive.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("Cannot map `null` into type `boolean`");
        assertThatThrownBy(() -> boot.readValue("{\"title\":\"t\",\"allowMemberOptions\":null}", WithPrimitive.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void 欠落とnullは_どちらもnullとして読まれ_区別できない() {
        CreatePollRequest missing = boot.readValue("{\"title\":\"t\",\"closesInMinutes\":30}", CreatePollRequest.class);
        CreatePollRequest explicitNull = boot.readValue(
                "{\"title\":\"t\",\"closesInMinutes\":30,\"allowMemberOptions\":null,\"options\":null}",
                CreatePollRequest.class);

        // コンパクトコンストラクタが「省略 = false」「選択肢なし = 空リスト」と決めているので、結果は同じ
        assertThat(missing).isEqualTo(explicitNull);
        assertThat(missing.allowMemberOptions()).isFalse();
        assertThat(missing.options()).isEmpty();
    }

    @Test
    void 同じキーが2回出てきたら_後のものが勝つ() {
        CreatePollRequest r = boot.readValue("{\"title\":\"a\",\"title\":\"b\",\"closesInMinutes\":30}",
                CreatePollRequest.class);

        assertThat(r.title()).isEqualTo("b");
    }

    @Test
    void 日時はUTCのISO8601文字列で出て_オフセット付きや数値でも読める() {
        Instant t = Instant.parse("2026-11-08T01:02:03.123456789Z");

        assertThat(boot.writeValueAsString(t)).isEqualTo("\"2026-11-08T01:02:03.123456789Z\"");
        assertThat(boot.readValue("\"2030-01-01T09:00:00+09:00\"", Instant.class))
                .isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        // 数値は「エポック秒」として読まれる。意図しない入力が通ってしまう例
        assertThat(boot.readValue("1893456000", Instant.class)).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        // 日付だけは読めない
        assertThatThrownBy(() -> boot.readValue("\"2030-01-01\"", Instant.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void 数値はJSONの文字列としては正確でも_JavaScriptでは2の53乗を超えると丸められる() {
        long beyondSafeInteger = 9_007_199_254_740_993L; // Number.MAX_SAFE_INTEGER (2^53 - 1) より大きい

        assertThat(boot.writeValueAsString(Map.of("id", beyondSafeInteger)))
                .isEqualTo("{\"id\":9007199254740993}"); // サーバーは正確に書く。JS の JSON.parse は 9007199254740992 にする
        assertThat(boot.writeValueAsString(new BigDecimal("33.40"))).isEqualTo("33.40");
        assertThat(boot.writeValueAsString(0.1 + 0.2)).isEqualTo("0.30000000000000004");
    }

    @Test
    void 内部の行をそのまま返すと_見せたくない項目まで出る() {
        Poll row = new Poll(1L, UUID.randomUUID(), "t", PollStatus.OPEN, "ABCDEFGH", false,
                Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-01-08T00:00:00Z"),
                Instant.parse("2029-12-31T23:00:00Z"), 0);

        String json = boot.writeValueAsString(row);

        // 内部の連番ID、参加コード、物理削除の時刻まで、参加者全員に見えてしまう
        assertThat(json).contains("\"id\":1", "\"joinCode\":\"ABCDEFGH\"", "\"deleteAfter\"");
    }
}
