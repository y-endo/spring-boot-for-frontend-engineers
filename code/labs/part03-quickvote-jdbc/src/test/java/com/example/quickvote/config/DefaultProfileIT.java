package com.example.quickvote.config;

import com.example.quickvote.TestcontainersConfiguration;
import com.example.quickvote.poll.DemoDataLoader;
import com.example.quickvote.poll.PollRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** プロファイルを指定しなければ、見本データの Bean は作られない（条件付きの Bean = 自動構成と同じ仕組み）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DefaultProfileIT {

    @Autowired
    ApplicationContext context;

    @Autowired
    PollRepository polls;

    @Test
    void 既定のプロファイルでは見本データのBeanは作られず_投票の表は空() {
        assertThat(context.getBeansOfType(DemoDataLoader.class)).isEmpty();
        assertThat(polls.findById(1)).isEmpty();
    }
}
