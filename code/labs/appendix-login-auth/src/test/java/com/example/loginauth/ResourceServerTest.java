package com.example.loginauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("resource")
class ResourceServerTest {

    @Autowired MockMvcTester mvc;

    @Test
    void トークンなしは401() {
        var r = mvc.get().uri("/api/hello").exchange();
        System.out.println("[resource] トークンなし -> " + r.getResponse().getStatus() + " WWW-Authenticate=" + r.getResponse().getHeader("WWW-Authenticate"));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void readスコープなら200() {
        var r = mvc.get().uri("/api/hello")
                .with(jwt().jwt(j -> j.subject("user-42")).authorities(new SimpleGrantedAuthority("SCOPE_read"))).exchange();
        System.out.println("[resource] scope=read -> " + r.getResponse().getStatus());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminスコープが無ければ403() {
        var r = mvc.get().uri("/api/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_read"))).exchange();
        System.out.println("[resource] admin API に scope=read -> " + r.getResponse().getStatus());
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void 壊れたBearerは401() {
        var r = mvc.get().uri("/api/hello").header("Authorization", "Bearer not-a-jwt").exchange();
        System.out.println("[resource] 壊れたトークン -> " + r.getResponse().getStatus() + " WWW-Authenticate=" + r.getResponse().getHeader("WWW-Authenticate"));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }
}
