package com.example.quickvote.common;

import org.springframework.http.HttpStatus;

/**
 * 業務エラーの一覧。code（名前）は ProblemDetail の "code" プロパティとして返し、クライアントが機械的に判定する。
 * HTTPステータスは粗い分類、code は細かい理由、という役割分担。
 * <p>
 * 名前・ステータス・メッセージは完成形（code/quickvote）と同じ。第2部の時点で使うものだけを載せている
 * （CSRF_REJECTED・RATE_LIMITED・IDEMPOTENCY_*・INVITE_INVALID などは、それぞれの部で加わる）。
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "入力値が正しくありません。"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "訪問者として識別できません。"),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "CSRFトークンが正しくありません。"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "この操作は許可されていません。"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "試行回数が多すぎます。しばらく待ってからやり直してください。"),
    HOST_ONLY(HttpStatus.FORBIDDEN, "この操作は主催者のみ行えます。"),
    MEMBER_OPTIONS_DISABLED(HttpStatus.FORBIDDEN, "この投票では参加者による選択肢の追加が許可されていません。"),
    POLL_NOT_FOUND(HttpStatus.NOT_FOUND, "投票が見つかりません。"),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "選択肢が見つかりません。"),
    JOIN_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "参加コードが見つかりません。"),
    POLL_FULL(HttpStatus.CONFLICT, "参加者が上限に達しています。"),
    OPTION_LIMIT_REACHED(HttpStatus.CONFLICT, "選択肢が上限に達しています。"),
    POLL_CLOSED(HttpStatus.CONFLICT, "この投票は締め切られています。"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部でエラーが発生しました。");

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }
}
