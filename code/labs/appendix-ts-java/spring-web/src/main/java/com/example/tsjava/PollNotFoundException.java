package com.example.tsjava;

class PollNotFoundException extends RuntimeException {
    PollNotFoundException(long id) {
        super("poll " + id + " not found");
    }
}
