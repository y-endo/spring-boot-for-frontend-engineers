package com.example.review2;

import com.example.review2.fixed.FixedApp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;

/** 修正版に同じ約束を流す。すべて緑になる。 */
@SpringBootTest(classes = FixedApp.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class FixedBoundaryTest extends BoundaryInvariantsTest {
}
