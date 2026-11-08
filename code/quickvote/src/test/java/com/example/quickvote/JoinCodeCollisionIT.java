package com.example.quickvote;

import java.time.Duration;
import java.util.List;

import com.example.quickvote.poll.CreatePollRequest;
import com.example.quickvote.poll.JoinCodeGenerator;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 参加コードの生成時の衝突は、UNIQUE制約違反を検知して別のコードで取り直す。
 * 31^8 通りの中での衝突は現実にはまず起きないので、ジェネレーターを差し替えて「必ず衝突する」状況を作る。
 */
class JoinCodeCollisionIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    JoinCodeGenerator generator;

    private CreatePollRequest request() {
        return new CreatePollRequest("衝突テスト", 60, false, List.of());
    }

    @Test
    void 既存のコードと衝突したら_別のコードで取り直して作成に成功する() {
        TestVisitor host = newVisitor();
        PollDetail existing = createPoll(host, false, "A");
        reset(generator);
        // 1〜3回目は既存と同じコードを返し（衝突）、4回目から本物の乱数に戻す
        doReturn(existing.joinCode()).doReturn(existing.joinCode()).doReturn(existing.joinCode())
                .doCallRealMethod().when(generator).generate();

        PollDetail created = pollService.create(host.id(), request());

        assertThat(created.joinCode()).isNotEqualTo(existing.joinCode()).hasSize(8);
        verify(generator, times(4)).generate();
        assertThat(countRows("polls")).isEqualTo(2);
        // 衝突した試行のトランザクションはロールバックされ、参加者などが中途半端に残らない
        assertThat(countRows("participants")).isEqualTo(2);
    }

    @Test
    void 衝突し続けたら上限回で諦めて失敗し_何も残らない() {
        TestVisitor host = newVisitor();
        PollDetail existing = createPoll(host, false, "A");
        reset(generator);
        doReturn(existing.joinCode()).when(generator).generate();

        Throwable thrown = catchThrowable(() -> pollService.create(host.id(), request()));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(generator, times(5)).generate();
        assertThat(countRows("polls")).isEqualTo(1);
    }
}
