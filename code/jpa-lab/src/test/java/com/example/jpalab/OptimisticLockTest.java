package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.example.jpalab.poll.OptionRepository;
import com.example.jpalab.poll.Poll;
import com.example.jpalab.poll.PollRepository;
import com.example.jpalab.support.AbstractLabTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** 第40章: @Version による楽観ロック。2人が同じ行を同時に書き換える。 */
class OptimisticLockTest extends AbstractLabTest {

    @Autowired
    PollRepository polls;

    @Autowired
    OptionRepository options;

    @Test
    void version_があると_後からコミットした側が失敗する(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        assertThatThrownBy(() -> tx.executeWithoutResult(a -> {
            // 主催者Aが編集画面を開いた（version=0 を読んだ）
            Poll pollA = polls.findById(1L).orElseThrow();
            // その間に、主催者Bが別トランザクションでタイトルを変えて保存した（version=1 になる）
            txNew.executeWithoutResult(b -> polls.findById(1L).orElseThrow().rename("Bが付けた題"));
            // Aが保存しようとする
            pollA.rename("Aが付けた題");
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .satisfies(e -> System.out.println("[楽観ロック] " + e.getClass().getSimpleName() + ": " + e.getMessage()));

        System.out.println("[楽観ロック] 発行されたSQL ↓");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        // Bの更新は残り、Aの更新は捨てられた。version は 0 → 1
        var row = jdbc.sql("SELECT title, version FROM polls WHERE id = 1").query().singleRow();
        System.out.println("[楽観ロック] DBの最終状態: " + row);
        assertThat(row.get("title")).isEqualTo("Bが付けた題");
        assertThat(row.get("version")).isEqualTo(1L);
    }

    @Test
    void 別の列を変えていても_同じ行なら衝突する() {
        seed(1, 2, 0);

        assertThatThrownBy(() -> tx.executeWithoutResult(a -> {
            Poll pollA = polls.findById(1L).orElseThrow();
            txNew.executeWithoutResult(b -> polls.findById(1L).orElseThrow().close(Instant.now())); // B: 締切
            pollA.rename("Aが付けた題"); // A: タイトル（別の列）
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void 画面で開いたあとに保存する_detached_な_Entity_でも検出される() {
        seed(1, 2, 0);
        // 編集画面を開いた時点の Entity（トランザクションが終わっているので detached）
        Poll opened = tx.execute(s -> polls.findById(1L).orElseThrow());
        // その間に別の人が保存
        tx.executeWithoutResult(s -> polls.findById(1L).orElseThrow().rename("別の人の題"));

        opened.rename("自分の題");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> polls.save(opened)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .satisfies(e -> System.out.println("[detachedのsave] " + e.getClass().getSimpleName() + ": " + e.getMessage()));
    }

    @Test
    void 壊してみる_versionがないEntityは_後勝ちで黙って上書きされる(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        tx.executeWithoutResult(a -> {
            var optionA = options.findById(1L).orElseThrow();
            txNew.executeWithoutResult(b -> options.findById(1L).orElseThrow().relabel("Bが付けたラベル"));
            optionA.relabel("Aが付けたラベル");
        }); // 例外は出ない

        sqlLines(output).forEach(line -> System.out.println("  " + line));
        String label = jdbc.sql("SELECT label FROM options WHERE id = 1").query(String.class).single();
        System.out.println("[versionなし] DBの最終状態: label=" + label + "（Bの変更は黙って消えた）");
        assertThat(label).isEqualTo("Aが付けたラベル");
    }
}
