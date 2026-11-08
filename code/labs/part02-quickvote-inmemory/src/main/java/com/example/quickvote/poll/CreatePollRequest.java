package com.example.quickvote.poll;

import java.util.List;

import com.example.quickvote.common.BoundedText;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 投票の作成リクエスト。options は初期の選択肢（省略可）。
 * 締切は絶対時刻ではなく「今から何分後か」（closesInMinutes）で受け取る。締切の時刻はサーバーが Clock から計算する
 * （通信にかかった時間でクライアントの時計とずれたり、端末の時計が狂っていたりしても、締切が変わらないようにするため）。
 * 形式・長さ・範囲は Bean Validation → 400。
 */
public record CreatePollRequest(
        @NotBlank @BoundedText(maxChars = PollRules.MAX_TEXT_CHARS) String title,
        @NotNull @Min(PollRules.MIN_CLOSES_IN_MINUTES) @Max(PollRules.MAX_CLOSES_IN_MINUTES) Integer closesInMinutes,
        Boolean allowMemberOptions,
        @Size(max = PollRules.MAX_OPTIONS) List<@NotBlank @BoundedText(maxChars = PollRules.MAX_TEXT_CHARS,
                maxBytes = PollRules.MAX_LABEL_BYTES) String> options) {

    /**
     * 前後の空白を落とし、省略された項目に既定値を入れる。検証はこの後の値に対して行われる。
     * allowMemberOptions を boolean（プリミティブ）にしないのは、Jackson 3 では record の
     * プリミティブ項目がJSONに無いとエラー(400)になるため。Boolean で受けて「省略 = false」と決めている。
     */
    public CreatePollRequest {
        title = title == null ? null : title.strip();
        allowMemberOptions = allowMemberOptions != null && allowMemberOptions;
        options = options == null ? List.of() : options.stream().map(s -> s == null ? null : s.strip()).toList();
    }
}
