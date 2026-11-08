package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import com.example.jpalab.poll.PollService;
import com.example.jpalab.support.AbstractLabTest;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;

/** 第39章: 永続コンテキストが何を肩代わりするか。 */
class EntityLifecycleTest extends AbstractLabTest {

    @Autowired
    PollService service;

    @Test
    void 世代の確認_Hibernate7とJakartaPersistence3_2() throws Exception {
        String hibernate = org.hibernate.Version.getVersionString();
        String persistenceApi = jakarta.persistence.Persistence.class.getPackage().getImplementationVersion();
        System.out.println("[世代] Hibernate ORM = " + hibernate + " / Jakarta Persistence API = " + persistenceApi);
        assertThat(hibernate).startsWith("7.");
        // Jakarta Persistence 3.2 で追加された API が存在する（3.1 以前には無い）
        assertThat(EntityManagerFactory.class.getMethod("runInTransaction", Consumer.class)).isNotNull();
    }

    @Test
    void save_を呼ばなくても_UPDATE_が出る(CapturedOutput output) {
        service.create("朝食は何がいい？", Instant.now().plusSeconds(3600), "パン", "ごはん");
        Long id = jdbc.sql("SELECT id FROM polls").query(Long.class).single();
        mark(output);

        service.rename(id, "昼食は何がいい？");

        System.out.println("[観測] rename 後の SQL と目印 ↓");
        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).startsWith("select");
        assertThat(lines.get(1)).contains("setter を呼ぶ直前");
        assertThat(lines.get(2)).contains("まだ UPDATE は出ていない");
        assertThat(lines.get(3)).startsWith("update polls set");
        // DB の値も変わっている
        assertThat(jdbc.sql("SELECT title FROM polls WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("昼食は何がいい？");
    }

    @Test
    void トランザクションがないと_変更は黙って捨てられる(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        service.renameWithoutTransaction(1L, "書き換えたつもり");

        List<String> sql = sqlLines(output);
        sql.forEach(line -> System.out.println("  " + line));
        assertThat(sql).hasSize(1).allMatch(line -> line.startsWith("select"));
        assertThat(jdbc.sql("SELECT title FROM polls WHERE id = 1").query(String.class).single()).isEqualTo("poll-1");
    }

    @Test
    void IDENTITY_は_save_の時点で_INSERT_が出る(CapturedOutput output) {
        service.create("朝食は何がいい？", Instant.now().plusSeconds(3600), "パン", "ごはん");

        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(lines).hasSize(5);
        assertThat(lines.get(0)).contains("save() を呼ぶ直前");
        assertThat(lines.get(1)).startsWith("insert into polls");
        assertThat(lines.get(2)).startsWith("insert into options");
        assertThat(lines.get(3)).startsWith("insert into options");
        assertThat(lines.get(4)).contains("save() が戻った直後");
    }

    @Test
    void 同じトランザクションで同じidを2回読むと_SQLは1本で同じオブジェクト(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        boolean same = service.readTwiceIsSameInstance(1L);

        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(same).isTrue();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("select");
        assertThat(lines.get(1)).contains("2回目の findById");
    }

    @Test
    void クエリの前に_変更が自動でflushされる(CapturedOutput output) {
        seed(1, 2, 0);
        mark(output);

        long count = service.renameThenCount(1L, "新しい題");

        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(count).isEqualTo(1);
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).startsWith("select");
        assertThat(lines.get(1)).contains("count クエリを実行");
        assertThat(lines.get(2)).startsWith("update polls set");
        assertThat(lines.get(3)).startsWith("select count");
    }

    @Test
    void 削除は_子を1行ずつ消してから_親を消す(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        service.delete(1L);

        List<String> lines = labAndSqlLines(output);
        lines.forEach(line -> System.out.println("  " + line));
        assertThat(lines.stream().filter(l -> l.startsWith("delete from options")).count()).isEqualTo(3);
        assertThat(lines.getLast()).startsWith("delete from polls");
    }
}
