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
 * <p>
 * 締切は絶対時刻ではなく「今から何分後か」（closesInMinutes、5〜10080）で受け取り、サーバーが closes_at を計算する。
 * 範囲は現在時刻に依存しない固定の規則なので、他の項目と同じ Bean Validation で検証できる（→ 400）。
 * 「現在時刻に依存する規則」（絶対時刻の締切が未来か等）は、サービス層で検証するしかなく、422 にする。
 * 相対指定にすればその必要がなく、ブラウザの時計のずれにも通信時間にも影響されない。
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
