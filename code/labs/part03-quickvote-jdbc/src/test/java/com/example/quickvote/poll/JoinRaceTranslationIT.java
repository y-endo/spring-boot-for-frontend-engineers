package com.example.quickvote.poll;

import java.util.List;
import java.util.Optional;

import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

/**
 * 第19章: 「確認」と「挿入」の間に、同じ人が割り込んだ状況を狙って作る。
 * 本物の同時実行ではなく、確認（findByPollAndVisitor）が1回だけ「まだいない」と答えるように細工する
 * （狙った競合を作る本格的な方法は第26章）。UNIQUE 制約が止め、DuplicateKeyException が JoinService で翻訳される。
 */
class JoinRaceTranslationIT extends AbstractIntegrationTest {

    @Autowired
    PollService pollService;

    @Autowired
    JoinService joinService;

    @MockitoSpyBean
    ParticipantRepository participants;

    @Test
    void 確認をすり抜けた二重参加は_UNIQUEが止めて_再参加と同じ成功になる() {
        PollDetail poll = pollService.create(1L, new CreatePollRequest("ランチ", 30, false, List.of("A")));
        joinService.join(2L, poll.joinCode()); // 1回目の参加は成功している

        // 2回目の参加の「確認」だけ、まだいないと答える。2回目以降の呼び出しは本物
        doReturn(Optional.empty()).doCallRealMethod().when(participants).findByPollAndVisitor(anyLong(), anyLong());
        JoinResponse second = joinService.join(2L, poll.joinCode());

        assertThat(second.role()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(jdbc.sql("SELECT count(*) FROM participants WHERE visitor_id = 2").query(Long.class).single())
                .isEqualTo(1L); // 行は増えていない
    }
}
