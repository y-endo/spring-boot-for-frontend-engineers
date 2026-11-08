package com.example.loginauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oauth2login")
class OAuth2LoginTest {

    @Autowired MockMvcTester mvc;

    @Test
    void 認可エンドポイントへのリダイレクトの中身を観察する() {
        var r = mvc.get().uri("/oauth2/authorization/google").exchange();
        String loc = r.getResponse().getHeader("Location");
        System.out.println("[oauth2login] status=" + r.getResponse().getStatus());
        var uri = UriComponentsBuilder.fromUri(URI.create(loc)).build();
        System.out.println("[oauth2login] host+path = " + uri.getHost() + uri.getPath());
        uri.getQueryParams().forEach((k, v) -> {
            String val = v.get(0);
            if (k.equals("state") || k.equals("nonce") || k.equals("code_challenge")) val = val.substring(0, Math.min(8, val.length())) + "...";
            System.out.println("[oauth2login]   " + k + " = " + val);
        });
        assertThat(loc).startsWith("https://accounts.google.com/");
        assertThat(uri.getQueryParams()).containsKeys("response_type", "client_id", "scope", "state", "redirect_uri", "nonce");
    }

    @Test
    void 未ログインの保護ページはログイン選択画面へ() {
        var r = mvc.get().uri("/me").exchange();
        System.out.println("[oauth2login] GET /me -> " + r.getResponse().getStatus() + " Location=" + r.getResponse().getHeader("Location"));
        assertThat(r.getResponse().getStatus()).isEqualTo(302);
    }
}
