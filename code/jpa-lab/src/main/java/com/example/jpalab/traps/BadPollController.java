package com.example.jpalab.traps;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 【第41章の演習】AI が書きそうな Controller。 */
@RestController
@RequestMapping("/api/bad")
public class BadPollController {

    private final BadPollService service;

    public BadPollController(BadPollService service) {
        this.service = service;
    }

    @GetMapping("/polls")
    public List<BadPoll> list() {
        return service.findAll();
    }
}
