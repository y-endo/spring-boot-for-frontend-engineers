package com.example.review3;

import com.example.review3.fixed.FixedApp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 修正版に同じ約束を流す。すべて緑になる。 */
@SpringBootTest(classes = FixedApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class FixedApiTest extends ApiInvariantsTest {
}
