package com.example.quickvote.common;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * すべての例外を RFC 9457 の ProblemDetail（application/problem+json）に変換する。
 * ResponseEntityExceptionHandler を継承すると、Spring MVC 自身の例外（JSONが壊れている、
 * メソッド違い、存在しないパス…）もまとめて ProblemDetail になる。
 */
@RestControllerAdvice
public class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ErrorCode code = ex.code();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.defaultDetail());
        problem.setProperty("code", code.name());
        problem.setInstance(occurrence());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
        if (ex.retryAfter() != null) {
            // 切り上げて最低1秒。Retry-After は整数秒で返す。
            long seconds = Math.max(1, (ex.retryAfter().toMillis() + 999) / 1000);
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        }
        return response.body(problem);
    }

    /**
     * 想定外の例外。スタックトレースは応答に出さず、ログに1回だけ出す。
     * ログには例外オブジェクトを渡す（メッセージ文字列に入力値を連結しない）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("想定外のエラーが発生しました", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.defaultDetail());
        problem.setProperty("code", code.name());
        problem.setInstance(occurrence());
        return ResponseEntity.status(code.status()).body(problem);
    }

    /** @Valid による検証エラー。入力値そのものは応答に含めず、項目名とメッセージだけ返す。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        return validationFailed(ex, errors, headers, status, request);
    }

    /** @RequestHeader などメソッド引数に付けた制約の検証エラー（Spring Framework 6.1 以降）。 */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validationFailed(ex, errors, headers, status, request);
    }

    private ResponseEntity<Object> validationFailed(Exception ex, List<FieldViolation> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.defaultDetail());
        problem.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Spring MVC 自身の例外（JSONが壊れている・パスがない・メソッド違い…）にも "code" を付ける。
     * 値はHTTPステータス名（例: BAD_REQUEST, NOT_FOUND）。
     * 本文(body)は null で渡ってくることがあり、基底クラスが ProblemDetail を作ってから返すので、後から付け足す。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", status != null ? status.name() : "ERROR");
        }
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setInstance(occurrence());
        }
        return response;
    }

    /**
     * ProblemDetail の instance（この問題の発生を指す識別子）には、リクエストのパスではなく相関IDを入れる。
     * 既定ではパスが入るが、招待トークンのように「パスに秘密を含む」APIでは、応答（やそれを記録するツール）に
     * 秘密を残してしまう。相関IDなら、ログ（requestId）と突き合わせて原因を追える。
     */
    private static URI occurrence() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return URI.create("urn:request-id:" + (requestId != null ? requestId : "unknown"));
    }
}
