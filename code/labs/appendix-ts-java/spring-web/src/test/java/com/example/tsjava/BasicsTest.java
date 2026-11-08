package com.example.tsjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Vitest:  describe("tally", () => { beforeEach(...); it("...", () => { expect(x).toBe(1) }) })
class BasicsTest {

    List<Integer> votes;

    @BeforeEach                                         // beforeEach(() => { votes = [] })
    void setUp() {
        votes = new ArrayList<>();
    }

    @Test                                               // it("adds", ...)
    void 追加すると増える() {
        votes.add(1);
        assertThat(votes).hasSize(1).containsExactly(1);   // expect(votes).toEqual([1])
    }

    @Test
    void 不正な引数は例外() {
        assertThatThrownBy(() -> Integer.parseInt("x"))    // expect(() => ...).toThrow(...)
                .isInstanceOf(NumberFormatException.class);
    }

    @Nested                                             // describe("nested", ...)
    class 空のとき {
        @Test
        void 空である() {
            assertThat(votes).isEmpty();
        }
    }

    @ParameterizedTest                                  // it.each([[1, 2, 3], ...])
    @CsvSource({"1,2,3", "10,20,30", "-1,1,0"})
    void 足し算(int a, int b, int expected) {
        assertThat(a + b).isEqualTo(expected);
    }

    @Test
    void JsonNodeで形を決めずに読む() {                       // JSON.parse(text) を any のまま触る
        JsonNode node = JsonMapper.builder().build().readTree("{\"title\":\"ランチ\",\"tags\":[\"a\",\"b\"]}");
        assertThat(node.get("title").asString()).isEqualTo("ランチ");
        assertThat(node.get("tags").size()).isEqualTo(2);
        assertThat(node.path("missing").isMissingNode()).isTrue();     // node.missing?.x に近い
    }
}
