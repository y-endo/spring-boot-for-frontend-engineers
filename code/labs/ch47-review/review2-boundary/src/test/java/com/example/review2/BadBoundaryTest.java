package com.example.review2;

import com.example.review2.bad.BadApp;

import org.junit.jupiter.api.Tag;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;

/** 欠陥入りのコードに約束を流す。赤くなるのが正しい（既定のビルドでは除外。README 参照）。 */
@Tag("red")
@SpringBootTest(classes = BadApp.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class BadBoundaryTest extends BoundaryInvariantsTest {
}
