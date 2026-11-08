package com.example.quickvote.poll;

import com.example.quickvote.common.BoundedText;

import jakarta.validation.constraints.NotBlank;

/** 選択肢ラベルは1〜100文字かつ UTF-8 で400バイト以内。 */
public record AddOptionRequest(
        @NotBlank @BoundedText(maxChars = PollRules.MAX_TEXT_CHARS, maxBytes = PollRules.MAX_LABEL_BYTES) String label) {

    public AddOptionRequest {
        label = label == null ? null : label.strip();
    }
}
