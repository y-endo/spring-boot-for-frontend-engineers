package com.example.quickvote;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.quickvote.common.IdempotencyRepository;
import com.example.quickvote.common.IdempotencyService;
import com.example.quickvote.poll.OptionResponse;
import com.example.quickvote.poll.PollDetail;
import com.example.quickvote.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 冪等性の記録と、定期削除の競合。
 * 「先取りが0件だった（記録が既にある）」と「その記録を読む」は別々のSQL文で、READ COMMITTED では文ごとに見える状態が変わる。
 * その間に期限切れの記録が削除されてコミットされると、読む側は記録を見つけられない（以前は 500 になっていた）。
 * 狙った順序を作るため、記録を読む直前に、別スレッド（別のDB接続）で削除を実行して確定させる。
 */
class IdempotencyRaceIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    IdempotencyRepository repository;

    /** 記録を読む1回目の直前に、定期削除が全記録を消してコミットする。 */
    private AtomicBoolean cleanerDeletesBeforeNextRead() {
        AtomicBoolean fired = new AtomicBoolean();
        doAnswer(invocation -> {
            if (fired.compareAndSet(false, true)) {
                // 別スレッド = 別トランザクション。呼び出し元のトランザクションとは独立にコミットされる。
                CompletableFuture.runAsync(() -> jdbc.sql("DELETE FROM idempotency_records").update()).join();
            }
            return invocation.callRealMethod();
        }).when(repository).findForShare(anyLong(), anyString());
        return fired;
    }

    @Test
    void 先取りが0件だった直後に記録が削除されても_500にならず先取りからやり直す() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, Duration.ofDays(3)); // 時間を進めても締切にならない長さ
        pollService.addOption(host.id(), poll.id(), "key-race", "うどん"); // 記録ができる
        AtomicBoolean fired = cleanerDeletesBeforeNextRead();

        // 保持期限の直前の再送。tryInsert は0件（記録がある）→ 読む直前に記録が消える → 先取りからやり直す
        clock.advance(Duration.ofHours(23).plusMinutes(59));
        IdempotencyService.Result<OptionResponse> result = pollService.addOption(host.id(), poll.id(), "key-race",
                "うどん");

        assertThat(fired).isTrue();
        // 記録は消えていたので、新しい操作として実行された（二重登録にはなるが、記録が消えたのは期限切れ＝仕様どおり）。
        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(201);
        assertThat(countRows("options")).isEqualTo(2);
        assertThat(countRows("idempotency_records")).isEqualTo(1); // やり直しで先取りした記録
        // 先取りの試行は、最初の登録(1回) + 再送(0件だった1回 + やり直しで成功した1回) = 3回
        verify(repository, times(3)).tryInsert(anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 記録が消えていなければ_従来どおり保存済みの応答を返す() {
        TestVisitor host = newVisitor();
        PollDetail poll = createPoll(host, false);
        OptionResponse first = pollService.addOption(host.id(), poll.id(), "key-keep", "うどん").body();

        IdempotencyService.Result<OptionResponse> retry = pollService.addOption(host.id(), poll.id(), "key-keep",
                "うどん");

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.body()).isEqualTo(first);
        assertThat(countRows("options")).isEqualTo(1);
        verify(repository, times(2)).tryInsert(anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }
}
