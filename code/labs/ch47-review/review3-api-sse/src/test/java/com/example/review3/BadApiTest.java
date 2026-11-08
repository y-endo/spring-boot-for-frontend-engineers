package com.example.review3;

import com.example.review3.bad.BadApp;

import org.junit.jupiter.api.Tag;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 欠陥入りのコードに約束を流す。赤くなるのが正しい（既定のビルドでは除外。README 参照）。 */
@Tag("red")
@SpringBootTest(classes = BadApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class BadApiTest extends ApiInvariantsTest {
}
