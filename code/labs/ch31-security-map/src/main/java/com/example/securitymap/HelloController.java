package com.example.securitymap;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "誰でも見てよい");
    }

    @GetMapping("/api/secret")
    public Map<String, String> secret() {
        return Map.of("message", "参加者だけに見せたい情報");
    }

    @PostMapping("/api/echo")
    public Map<String, String> echo(@RequestBody Map<String, String> body) {
        return body;
    }
}
