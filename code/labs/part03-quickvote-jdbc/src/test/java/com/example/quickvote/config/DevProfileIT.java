package com.example.quickvote.config;

import com.example.quickvote.TestcontainersConfiguration;
import com.example.quickvote.common.QuickVoteProperties;
import com.example.quickvote.poll.DemoDataLoader;
import com.example.quickvote.poll.PollRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** dev プロファイルでは application-dev.yaml が重なり、見本データを作る DemoDataLoader が Bean になって起動時に動く。 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevProfileIT {

    @Autowired
    ApplicationContext context;

    @Autowired
    QuickVoteProperties properties;

    @Autowired
    PollRepository polls;

    @Test
    void devプロファイルでは見本データが有効になり_起動時に見本の投票が作られる() {
        assertThat(properties.demo().seed()).isTrue();
        assertThat(context.getBeansOfType(DemoDataLoader.class)).hasSize(1);
        assertThat(polls.findById(1).orElseThrow().title()).isEqualTo("ランチどこ行く？");
    }
}
