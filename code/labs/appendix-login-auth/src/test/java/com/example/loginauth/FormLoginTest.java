package com.example.loginauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("form")
class FormLoginTest {

    @Autowired MockMvcTester mvc;

    @Test
    void 未ログインはログイン画面へ302() {
        var r = mvc.get().uri("/me").exchange();
        System.out.println("[form] 未ログイン GET /me -> " + r.getResponse().getStatus() + " Location=" + r.getResponse().getHeader("Location"));
        assertThat(r.getResponse().getStatus()).isEqualTo(302);
    }

    @Test
    void ログインに成功するとセッションが作られる() {
        var r = mvc.post().uri("/login").with(csrf())
                .param("username", "alice").param("password", "s3cret-pass").exchange();
        System.out.println("[form] 正しい資格情報 POST /login -> " + r.getResponse().getStatus()
                + " Location=" + r.getResponse().getHeader("Location")
                + " session=" + (r.getRequest().getSession(false) != null));
        assertThat(r.getResponse().getStatus()).isEqualTo(302);
        assertThat(r.getResponse().getHeader("Location")).doesNotContain("error");
    }

    @Test
    void パスワード違いはerrorへ() {
        var r = mvc.post().uri("/login").with(csrf())
                .param("username", "alice").param("password", "nope").exchange();
        System.out.println("[form] 誤った資格情報 POST /login -> " + r.getResponse().getStatus() + " Location=" + r.getResponse().getHeader("Location"));
        assertThat(r.getResponse().getHeader("Location")).contains("error");
    }

    @Test
    void CSRFトークンなしのログインは403() {
        var r = mvc.post().uri("/login").param("username", "alice").param("password", "s3cret-pass").exchange();
        System.out.println("[form] CSRFなし POST /login -> " + r.getResponse().getStatus());
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
    }
}
