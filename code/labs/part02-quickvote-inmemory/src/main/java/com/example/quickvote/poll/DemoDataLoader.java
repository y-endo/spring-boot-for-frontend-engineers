package com.example.quickvote.poll;

import java.util.List;

import com.example.quickvote.common.QuickVoteProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 開発用の見本データ。quickvote.demo.seed=true のときだけ Bean になる（dev プロファイルで有効）。
 * 訪問者1が主催する投票を1つ作り、訪問者2〜（members 人）を参加させる。参加コードはログに出さない
 * （主催者として GET /api/polls/{id} を呼べば分かる）。
 */
@Component
@ConditionalOnProperty(name = "quickvote.demo.seed", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final PollService pollService;
    private final JoinService joinService;
    private final QuickVoteProperties properties;

    public DemoDataLoader(PollService pollService, JoinService joinService, QuickVoteProperties properties) {
        this.pollService = pollService;
        this.joinService = joinService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        PollDetail poll = pollService.create(1L, new CreatePollRequest("ランチどこ行く？", 60, true,
                List.of("カレー", "ラーメン", "そば")));
        for (int i = 0; i < properties.demo().members(); i++) {
            joinService.join(2L + i, poll.joinCode());
        }
        log.info("見本の投票を作りました: id={}（主催者は X-Visitor-Id: 1、参加者は 2〜{}）", poll.id(),
                1 + properties.demo().members());
    }
}
