package com.example.quickvote.common;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * 想定している業務エラー（上限超過・権限なし・存在しない、など）。
 * メッセージには入力値（選択肢ラベルなど）を入れない。ログや応答に漏れても安全にするため。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final @Nullable Duration retryAfter;

    public ApiException(ErrorCode code) {
        this(code, null);
    }

    /** retryAfter は 429 のときの Retry-After ヘッダー用。 */
    public ApiException(ErrorCode code, @Nullable Duration retryAfter) {
        super(code.name());
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
