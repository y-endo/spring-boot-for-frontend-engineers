package com.example.quickvote.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JUnit は、テストメソッドごとにこのクラスの新しいインスタンスを作る。テストは互いに独立する。 */
class LifecycleTest {

    private int calls = 0; // フィールドは、テストごとに 0 から始まる
    private List<String> options;

    @BeforeEach // 各テストの直前に毎回実行される
    void setUp() {
        options = new ArrayList<>(List.of("カレー", "ラーメン"));
    }

    @Test
    void firstTest() {
        calls++;
        options.add("そば");
        assertThat(calls).isEqualTo(1);
        assertThat(options).hasSize(3);
    }

    @Test
    void secondTest() {
        calls++;
        assertThat(calls).isEqualTo(1); // 別のインスタンスなので、firstTest の影響を受けない
        assertThat(options).hasSize(2); // setUp が毎回やり直されるので、そばは入っていない
    }
}
