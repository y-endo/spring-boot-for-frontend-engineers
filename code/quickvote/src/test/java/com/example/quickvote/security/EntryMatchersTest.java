package com.example.quickvote.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入口の判定。生の URI 文字列の比較ではなく、MVC と同じ（デコード後・コンテキストパスを除いた）パスで判定すること。
 */
class EntryMatchersTest {

    private static MockHttpServletRequest request(String method, String contextPath, String rawUri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, rawUri);
        request.setContextPath(contextPath);
        return request;
    }

    @ParameterizedTest(name = "{0} {2} (コンテキストパス \"{1}\")")
    @CsvSource(delimiter = '|', value = {
            "POST | | /api/join",
            "POST | | /api/%6aoin",              // %6a = j。MVC は /api/join として処理する
            "POST | | /api/%6Aoin",              // 16進の大文字小文字
            "POST | /quickvote | /quickvote/api/join",
            "POST | /quickvote | /quickvote/api/%6aoin",
    })
    void 参加コードの入口は_エンコードやコンテキストパスがあっても判定される(String method, String contextPath, String uri) {
        MockHttpServletRequest request = request(method, contextPath == null ? "" : contextPath.strip(), uri.strip());

        assertThat(EntryMatchers.JOIN.matches(request)).isTrue();
        assertThat(EntryMatchers.issuesVisitor(request)).isTrue();
        assertThat(EntryMatchers.isAttempt(request)).isTrue();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "POST | /api/invites/abc123/accept",
            "POST | /api/invites/%61bc123/accept",   // %61 = a
            "POST | /api/%69nvites/abc123/accept",   // %69 = i
    })
    void 招待の受諾の入口は_エンコードされていても判定される(String method, String uri) {
        MockHttpServletRequest request = request(method, "", uri.strip());

        assertThat(EntryMatchers.ACCEPT_INVITE.matches(request)).isTrue();
        assertThat(EntryMatchers.issuesVisitor(request)).isTrue();
        assertThat(EntryMatchers.isAttempt(request)).isTrue();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "POST | /api/%70olls",
            "POST | /api/polls",
    })
    void 投票の作成の入口(String method, String uri) {
        MockHttpServletRequest request = request(method, "", uri.strip());

        assertThat(EntryMatchers.CREATE_POLL.matches(request)).isTrue();
        assertThat(EntryMatchers.issuesVisitor(request)).isTrue();
        assertThat(EntryMatchers.isCreation(request)).isTrue();
        assertThat(EntryMatchers.isAttempt(request)).isFalse();
    }

    @Test
    void 招待の発行は_回数を数えるが_訪問者は発行しない() {
        MockHttpServletRequest request = request("POST", "", "/api/polls/3f2504e0-4f89-41d3-9a0c-0305e82c3301/invites");

        assertThat(EntryMatchers.ISSUE_INVITE.matches(request)).isTrue();
        assertThat(EntryMatchers.isCreation(request)).isTrue();
        assertThat(EntryMatchers.issuesVisitor(request)).isFalse();
    }

    @ParameterizedTest(name = "{0} {1} は対象外")
    @CsvSource(delimiter = '|', value = {
            "GET  | /api/join",                    // メソッドが違う
            "PUT  | /api/polls",
            "POST | /api/join/",                   // 末尾のスラッシュ。MVC は 404 にする
            "POST | /api/joinx",
            "POST | /api/join/extra",
            "POST | /api/jo",
            "POST | /api/invites//accept",         // トークンが空
            "POST | /api/invites/abc/accept/more",
            "POST | /api/polls/abc",
            "POST | /api/pollsx",
            "POST | /other/api/join",
            "POST | /api/JOIN",                    // MVC は大文字小文字を区別する
    })
    void 入口ではないリクエストは判定されない(String method, String uri) {
        MockHttpServletRequest request = request(method.strip(), "", uri.strip());

        assertThat(EntryMatchers.issuesVisitor(request)).isFalse();
        assertThat(EntryMatchers.isAttempt(request)).isFalse();
        assertThat(EntryMatchers.isCreation(request)).isFalse();
    }
}
