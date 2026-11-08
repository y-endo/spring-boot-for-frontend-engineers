package com.example.quickvote;

import java.util.List;
import java.util.Map;

import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** 本番設定（quickvote.visitor-cookie.secure=true）では、Cookie に Secure 属性が付く。 */
@SpringBootTest(properties = "quickvote.visitor-cookie.secure=true")
class CookieSecureIT extends AbstractIntegrationTest {

    @Test
    void 訪問者Cookieに_Secure属性が付く() {
        MvcTestResult result = request(HttpMethod.POST, "/api/polls", null,
                Map.of("title", "x", "closesInMinutes", 60));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(c -> c.startsWith("QV_VISITOR=") && c.contains("Secure") && c.contains("HttpOnly")
                && c.contains("SameSite=Lax"));
    }
}
