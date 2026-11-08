package com.example.loginauth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    @GetMapping("/me")
    String me(Authentication auth) {
        return auth.getName() + " via " + auth.getClass().getSimpleName();
    }

    @GetMapping("/api/hello")
    String hello(Authentication auth) {
        return "hello " + auth.getName();
    }

    @GetMapping("/api/admin/ping")
    String admin() {
        return "admin ok";
    }
}
