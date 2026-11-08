package com.example.review1;

import com.example.review1.fixed.FixedApp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 修正版に同じ約束を流す。すべて緑になる。 */
@SpringBootTest(classes = FixedApp.class)
@Import(TestcontainersConfig.class)
class FixedPollMembersTest extends PollMembersInvariantsTest {
}
