package com.example.review1;

import com.example.review1.bad.BadApp;

import org.junit.jupiter.api.Tag;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 欠陥入りのコードに約束を流す。赤くなるのが正しい（既定のビルドでは除外。README 参照）。 */
@Tag("red")
@SpringBootTest(classes = BadApp.class)
@Import(TestcontainersConfig.class)
class BadPollMembersTest extends PollMembersInvariantsTest {
}
