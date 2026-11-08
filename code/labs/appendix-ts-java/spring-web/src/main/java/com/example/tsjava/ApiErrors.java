package com.example.tsjava;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Express:  app.use((err, req, res, next) => { ... })   エラー処理ミドルウェア
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(PollNotFoundException.class)
    ProblemDetail notFound(PollNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Poll not found");
        pd.setProperty("code", "POLL_NOT_FOUND");
        return pd;
    }
}
