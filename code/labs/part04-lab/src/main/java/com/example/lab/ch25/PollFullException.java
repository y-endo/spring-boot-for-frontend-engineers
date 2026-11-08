package com.example.lab.ch25;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 人数が上限に達している（QuickVote 本体の ErrorCode.POLL_FULL = 409 に相当）。 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PollFullException extends RuntimeException {

    public PollFullException(long pollId) {
        super("poll " + pollId + " は満員");
    }
}
