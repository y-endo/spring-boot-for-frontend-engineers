package com.example.quickvote.di;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第11章の「壊してみる」。DIコンテナが起動時に何を検査し、どう失敗するかを、コンテナだけ（Webサーバーなし）で確かめる。
 */
class DiFailureTest {

    // ---- 登場人物（本番のクラスに似せた最小の部品）----

    static class Repo {
        String find() {
            return "row";
        }
    }

    static class Service {
        private final Repo repo;

        Service(Repo repo) { // コンストラクタ注入: 「Repo が必要」と型で宣言している
            this.repo = repo;
        }

        String run() {
            return repo.find();
        }
    }

    interface ShareFormatter {
        String format(int percent);
    }

    static class PlainFormatter implements ShareFormatter {
        public String format(int percent) {
            return percent + "%";
        }
    }

    static class FancyFormatter implements ShareFormatter {
        public String format(int percent) {
            return "★" + percent + "%★";
        }
    }

    static class Report {
        private final ShareFormatter formatter;

        Report(ShareFormatter formatter) {
            this.formatter = formatter;
        }

        String line() {
            return formatter.format(50);
        }
    }

    static class A {
        A(B b) {
        }
    }

    static class B {
        B(A a) {
        }
    }

    /** 古い記事によく出る書き方（フィールド注入）。自分で new すると注入されない。 */
    static class LegacyService {
        @Autowired
        private Repo repo;

        String run() {
            return repo.find();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    // ---- 壊してみる ----

    @Test
    void 必要な部品がコンテナにいなければ_起動時に落ちる() {
        runner.withBean(Service.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable root = rootCause(ctx.getStartupFailure());
            System.out.println("[demo] 未登録: " + root.getMessage());
            assertThat(ctx.getStartupFailure()).isInstanceOf(UnsatisfiedDependencyException.class);
            assertThat(root).isInstanceOf(NoSuchBeanDefinitionException.class)
                    .hasMessageContaining("No qualifying bean of type")
                    .hasMessageContaining("Repo");
        });
    }

    @Test
    void 同じ型の候補が2つあると_どちらか決められず落ちる() {
        runner.withBean(PlainFormatter.class).withBean(FancyFormatter.class).withBean(Report.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable root = rootCause(ctx.getStartupFailure());
            System.out.println("[demo] 候補が2つ: " + root.getMessage());
            assertThat(root).isInstanceOf(NoUniqueBeanDefinitionException.class);
        });
    }

    @Configuration
    static class PrimaryChoice {
        @Bean
        @Primary // 迷ったらこちら
        ShareFormatter fancy() {
            return new FancyFormatter();
        }

        @Bean
        ShareFormatter plain() {
            return new PlainFormatter();
        }

        @Bean
        Report report(ShareFormatter formatter) {
            return new Report(formatter);
        }
    }

    @Configuration
    static class QualifierChoice {
        @Bean
        ShareFormatter fancy() {
            return new FancyFormatter();
        }

        @Bean
        ShareFormatter plain() {
            return new PlainFormatter();
        }

        @Bean
        Report report(@Qualifier("plain") ShareFormatter formatter) { // 名前で指名する
            return new Report(formatter);
        }
    }

    @Test
    void Primaryで既定を決めるか_Qualifierで名前を指名すれば解決する() {
        new ApplicationContextRunner().withUserConfiguration(PrimaryChoice.class).run(ctx ->
                assertThat(ctx.getBean(Report.class).line()).isEqualTo("★50%★"));
        new ApplicationContextRunner().withUserConfiguration(QualifierChoice.class).run(ctx ->
                assertThat(ctx.getBean(Report.class).line()).isEqualTo("50%"));
    }

    @Test
    void コンストラクタで互いを要求し合うと_循環参照で落ちる() {
        runner.withBean(A.class).withBean(B.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable root = rootCause(ctx.getStartupFailure());
            System.out.println("[demo] 循環: " + root.getClass().getSimpleName() + ": "
                    + root.getMessage().lines().findFirst().orElse(""));
            assertThat(root).hasMessageContaining("circular");
        });
    }

    @Test
    void フィールド注入のクラスを自分でnewすると_注入されずNPEになる() {
        LegacyService service = new LegacyService(); // コンテナを通していない

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(service::run);

        System.out.println("[demo] new した場合: " + thrown.getMessage());
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void コンテナに作らせれば_同じクラスにも注入される() {
        runner.withBean(Repo.class).withBean(LegacyService.class).run(ctx ->
                assertThat(ctx.getBean(LegacyService.class).run()).isEqualTo("row"));
    }

    @Test
    void コンストラクタ注入なら_newするとき部品を渡さないとコンパイルできない() {
        // new Service();            // ← コンパイルエラー（引数が足りない）。忘れる余地がない
        Service service = new Service(new Repo()); // テストでは偽物を手で渡せる。コンテナもSpringも要らない
        assertThat(service.run()).isEqualTo("row");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
