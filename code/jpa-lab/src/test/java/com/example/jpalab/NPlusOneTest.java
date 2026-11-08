package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.example.jpalab.poll.Poll;
import com.example.jpalab.poll.PollRepository;
import com.example.jpalab.poll.PollService;
import com.example.jpalab.poll.PollSummary;
import com.example.jpalab.poll.PollView;
import com.example.jpalab.support.AbstractLabTest;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.data.domain.PageRequest;

/** 第40章: 投票100件の一覧で、SQLは何本流れるか。 */
class NPlusOneTest extends AbstractLabTest {

    @Autowired
    PollService service;

    @Autowired
    PollRepository polls;

    @Test
    void 遅延ロード任せの一覧は_1プラスN本(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollView>> m = measure(service::listLazy);

        List<String> sql = sqlLines(output);
        System.out.println("[N+1] 遅延ロード: Hibernate統計=" + m.sqlCount() + "本 / ログに出たSQL=" + sql.size() + "本");
        sql.stream().limit(4).forEach(line -> System.out.println("  " + line));
        System.out.println("  ...");
        assertThat(m.result()).hasSize(100);
        assertThat(m.result().getFirst().optionLabels()).containsExactly("option-1", "option-2", "option-3");
        assertThat(m.sqlCount()).isEqualTo(101);
        assertThat(sql).hasSize(101);
        assertThat(sql.get(0)).startsWith("select p1_0.id");
        assertThat(sql.subList(1, 101)).allMatch(s -> s.startsWith("select o1_0.poll_id"));
    }

    @Test
    void fetch_join_は_1本(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollView>> m = measure(service::listFetchJoin);

        List<String> sql = sqlLines(output);
        System.out.println("[fetch join] Hibernate統計=" + m.sqlCount() + "本");
        sql.forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).hasSize(100);
        assertThat(m.sqlCount()).isEqualTo(1);
    }

    @Test
    void EntityGraph_は_1本(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollView>> m = measure(service::listEntityGraph);

        System.out.println("[EntityGraph] Hibernate統計=" + m.sqlCount() + "本");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).hasSize(100);
        assertThat(m.sqlCount()).isEqualTo(1);
    }

    @Test
    void DTO射影は_1本(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollView>> m = measure(service::listDto);

        System.out.println("[DTO射影] Hibernate統計=" + m.sqlCount() + "本");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).hasSize(100);
        assertThat(m.result().getFirst().optionLabels()).containsExactly("option-1", "option-2", "option-3");
        assertThat(m.sqlCount()).isEqualTo(1);
    }

    @Test
    void 集計もDBにやらせるDTOは_1本(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<PollSummary>> m = measure(() -> tx.execute(s -> polls.findSummaries()));

        System.out.println("[集計DTO] Hibernate統計=" + m.sqlCount() + "本");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).hasSize(100);
        assertThat(m.result().getFirst().optionCount()).isEqualTo(3);
        assertThat(m.sqlCount()).isEqualTo(1);
    }

    @Test
    void fetch_join_とページングを組み合わせる(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<Integer> m = measure(() -> tx.execute(s -> polls.findPageWithOptions(PageRequest.of(0, 10))
                .getContent().size()));

        System.out.println("[fetch join + ページング] Hibernate統計=" + m.sqlCount() + "本");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).isEqualTo(10);
        // Hibernate 7.4 は、親を絞り込むサブクエリ（offset / fetch first）の中身を JOIN する SQL を作る
        assertThat(sqlLines(output).getFirst()).contains("fetch first ? rows only");
    }

    @Test
    void 定石_先にIDだけをページングで取り_そのIDでfetch_join(CapturedOutput output) {
        seed(100, 3, 0);
        mark(output);

        Measured<List<Poll>> m = measure(() -> tx.execute(s -> {
            List<Long> ids = polls.findIds(PageRequest.of(1, 10));
            List<Poll> page = polls.findWithOptionsByIdIn(ids);
            page.forEach(p -> p.getOptions().size());
            return page;
        }));

        System.out.println("[IDページング→fetch join] Hibernate統計=" + m.sqlCount() + "本");
        sqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(m.result()).hasSize(10);
        assertThat(m.result().getFirst().getId()).isEqualTo(11L);
        assertThat(m.sqlCount()).isEqualTo(2);
    }

    /**
     * 観察: コレクションの fetch join とページングを組み合わせると、使い方によっては件数が絞られない。
     * Hibernate 7.4.13.Final では、同じ JPQL を「ページングなし」→「あり」の順で実行すると、後者も全件が返った。
     * 第40章の結論（IDを先にページングで取る2段階にする）の根拠の1つ。Hibernate の更新でこのテストが落ちたら挙動が変わった合図。
     */
    @Test
    void 壊してみる_同じJPQLをページングなしとありで続けて使う() {
        seed(100, 3, 0);
        String jpql = "select p from Poll p join fetch p.options order by p.id, p.title";

        int[] sizes = tx.execute(s -> {
            var em = emf.createEntityManager();
            try {
                int unpaged = em.createQuery(jpql, Poll.class).getResultList().size();
                em.clear();
                int paged = em.createQuery(jpql, Poll.class).setFirstResult(0).setMaxResults(10).getResultList().size();
                return new int[] { unpaged, paged };
            } finally {
                em.close();
            }
        });

        System.out.println("[同じJPQLを続けて実行] ページングなし=" + sizes[0] + "件 / 10件に絞ったはずが=" + sizes[1] + "件");
        assertThat(sizes[0]).isEqualTo(100);
        assertThat(sizes[1]).isEqualTo(100);
    }

    @Test
    void トランザクションの外で遅延ロードすると_LazyInitializationException() {
        seed(1, 3, 0);

        Poll poll = service.findEntity(1L); // ここでトランザクションは終わっている

        assertThatThrownBy(() -> poll.getOptions().size())
                .isInstanceOf(LazyInitializationException.class)
                .satisfies(e -> System.out.println("[LazyInitializationException] " + e.getMessage()));
    }

    @Test
    void トランザクションの中なら_同じコードが動く() {
        seed(1, 3, 0);

        List<String> labels = tx.execute(s -> polls.findById(1L).orElseThrow().getOptions().stream()
                .map(o -> o.getLabel()).toList());

        assertThat(labels).containsExactly("option-1", "option-2", "option-3");
    }
}
