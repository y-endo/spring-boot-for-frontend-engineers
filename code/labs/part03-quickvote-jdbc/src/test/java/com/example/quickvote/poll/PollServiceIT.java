package com.example.quickvote.poll;

import java.time.Instant;
import java.util.List;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.support.AbstractIntegrationTest;
import com.example.quickvote.vote.VoteService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service を、実PostgreSQLにつないだまま確かめるテスト（HTTP は通さない）。
 * 第2部の PollServiceTest は new で部品を手組みし、Repository にメモリ版を使っていた。
 * Repository が SQL になった今は、本物のDBが要る。Spring に配線を任せ、DBはテストごとに空にする。
 */
class PollServiceIT extends AbstractIntegrationTest {

    @Autowired
    PollService pollService;

    @Autowired
    JoinService joinService;

    @Autowired
    VoteService voteService;

    @Test
    void 作った人はHOSTになり_参加コードが見える() {
        PollDetail detail = pollService.create(1L, new CreatePollRequest("ランチ", 30, false, List.of("カレー", "そば")));

        assertThat(detail.role()).isEqualTo("HOST");
        assertThat(detail.joinCode()).hasSize(8);
        assertThat(detail.closesAt()).isAfter(Instant.now().plusSeconds(29 * 60));
        assertThat(detail.options()).extracting(PollDetail.OptionView::label).containsExactly("カレー", "そば");
    }

    @Test
    void 参加者にはMEMBERとして見え_参加コードは見えない() {
        PollDetail created = pollService.create(1L, new CreatePollRequest("ランチ", 30, false, List.of("カレー")));
        joinService.join(2L, created.joinCode());

        PollDetail seen = pollService.get(2L, created.id());

        assertThat(seen.role()).isEqualTo("MEMBER");
        assertThat(seen.joinCode()).isNull();
        assertThat(seen.participantCount()).isEqualTo(2);
    }

    @Test
    void 参加していない人には_存在しない投票と同じ404() {
        PollDetail created = pollService.create(1L, new CreatePollRequest("ランチ", 30, false, List.of()));

        assertThatThrownBy(() -> pollService.get(99L, created.id()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.POLL_NOT_FOUND));
    }

    @Test
    void 票が3つに割れても_得票率の合計は100() {
        PollDetail created = pollService.create(1L, new CreatePollRequest("3択", 30, false, List.of("A", "B", "C")));
        joinService.join(2L, created.joinCode());
        joinService.join(3L, created.joinCode());
        List<Long> optionIds = created.options().stream().map(PollDetail.OptionView::id).toList();
        voteService.vote(1L, created.id(), optionIds.get(0));
        voteService.vote(2L, created.id(), optionIds.get(1));
        voteService.vote(3L, created.id(), optionIds.get(2));

        PollDetail detail = pollService.get(1L, created.id());

        assertThat(detail.options()).extracting(o -> o.percent().toPlainString())
                .containsExactly("33.4", "33.3", "33.3");
    }
}
