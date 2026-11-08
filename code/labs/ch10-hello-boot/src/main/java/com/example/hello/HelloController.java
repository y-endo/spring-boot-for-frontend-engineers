package com.example.hello;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Hello hello() {
        return new Hello("こんにちは、QuickVote", Instant.now());
    }

    public record Hello(String message, Instant at) {
    }
}
