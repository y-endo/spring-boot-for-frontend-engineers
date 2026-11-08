package com.example.boot34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;               // 旧: org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;           // 旧: org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Service
class Greeter {
    String greet() { return "real"; }
}

@RestController
class GreetController {
    private final Greeter greeter;
    GreetController(Greeter greeter) { this.greeter = greeter; }

    @GetMapping("/api/greet")
    String greet() { return greeter.greet(); }
}

@WebMvcTest(GreetController.class)
@org.springframework.context.annotation.Import(SecurityConfig.class)
class NewTestFormsTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean Greeter greeter;            // 旧: @MockBean

    @Test
    @WithMockUser
    void モックに差し替わる() {
        given(greeter.greet()).willReturn("mocked");
        var result = mvc.get().uri("/api/greet").exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().isEqualTo("mocked");
    }

    @Test
    void 未ログインは401か302() {
        var result = mvc.get().uri("/api/greet").exchange();
        assertThat(result.getResponse().getStatus()).isIn(401, 302);
    }
}
