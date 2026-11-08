package com.example.review3.fixed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 想定内のエラー（検証・404 など）は基底クラスが ProblemDetail にする。
 * 想定外は、応答に原因を出さず（INTERNAL_ERROR だけ）、ログには例外の型だけを1回出す。
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        // 例外のメッセージには SQL や入力値が入ることがある。型だけを記録する（本編の SafeThrowable の簡易版）。
        log.error("想定外のエラー type={}", ex.getClass().getName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "サーバー内部でエラーが発生しました。");
        problem.setProperty("code", "INTERNAL_ERROR");
        return problem;
    }
}
