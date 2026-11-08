package com.example.quickvote.config;

import java.util.Map;

import com.example.quickvote.common.QuickVoteProperties;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** 第15章: 設定値のバインド・既定値・起動時の検証（fail fast）・環境変数。プロファイルは DevProfileTest / DefaultProfileTest。 */
class ConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(QuickVoteProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    void 何も書かなければ_record側の既定値が使われる() {
        runner.run(ctx -> {
            QuickVoteProperties p = ctx.getBean(QuickVoteProperties.class);
            assertThat(p.standInAuth().header()).isEqualTo("X-Visitor-Id");
            assertThat(p.demo().seed()).isFalse();
            assertThat(p.demo().members()).isEqualTo(3);
        });
    }

    @Test
    void kebab_case_でもcamelCaseでも_同じ項目に束縛される() {
        runner.withPropertyValues("quickvote.stand-in-auth.header=X-A").run(ctx ->
                assertThat(ctx.getBean(QuickVoteProperties.class).standInAuth().header()).isEqualTo("X-A"));
        runner.withPropertyValues("quickvote.standInAuth.header=X-B").run(ctx ->
                assertThat(ctx.getBean(QuickVoteProperties.class).standInAuth().header()).isEqualTo("X-B"));
    }

    @Test
    void 環境変数の名前でも束縛される() {
        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-systemEnvironment", Map.of(
                        "QUICKVOTE_DEMO_MEMBERS", "5",
                        "QUICKVOTE_STANDINAUTH_HEADER", "X-From-Env"))))
                .run(ctx -> {
                    QuickVoteProperties p = ctx.getBean(QuickVoteProperties.class);
                    assertThat(p.demo().members()).isEqualTo(5);
                    assertThat(p.standInAuth().header()).isEqualTo("X-From-Env");
                });
    }

    @Test
    void 範囲外の値は起動時に落ちる() {
        runner.withPropertyValues("quickvote.demo.members=99").run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable root = root(ctx.getStartupFailure());
            System.out.println("[demo] 範囲外: " + ctx.getStartupFailure().getMessage().lines().findFirst().orElse(""));
            assertThat(root.getMessage()).contains("demo.members");
        });
    }

    @Test
    void 空文字のヘッダー名も起動時に落ちる() {
        runner.withPropertyValues("quickvote.stand-in-auth.header=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(root(ctx.getStartupFailure()).getMessage()).contains("standInAuth.header");
        });
    }

    @Test
    void 数値のはずの項目に文字列を書いても落ちる() {
        runner.withPropertyValues("quickvote.demo.members=たくさん").run(ctx -> {
            assertThat(ctx).hasFailed();
            System.out.println("[demo] 型違い: " + ctx.getStartupFailure().getMessage().lines().findFirst().orElse(""));
        });
    }

    @Test
    void 設定キーのタイポは_エラーにならず黙って無視され_既定値が使われる() {
        // 正しくは stand-in-auth.header。heder と綴りを間違えた
        runner.withPropertyValues("quickvote.stand-in-auth.heder=X-Typo").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(QuickVoteProperties.class).standInAuth().header()).isEqualTo("X-Visitor-Id");
        });
    }

    @Test
    void 環境変数の置き忘れ_未解決のプレースホルダー_は_形式の検証で起動時に落とせる() {
        // 環境変数 QV_HEADER_UNSET が無いと、${...} は展開されず文字列のまま束縛される（それ自体はエラーにならない）。
        // ヘッダー名として使えない文字（$ { }）を @Pattern で弾くので、起動時に気づける。
        runner.withPropertyValues("quickvote.stand-in-auth.header=${QV_HEADER_UNSET}").run(ctx -> {
            assertThat(ctx).hasFailed();
            System.out.println("[demo] 未解決のプレースホルダー: " + root(ctx.getStartupFailure()).getMessage());
            assertThat(root(ctx.getStartupFailure()).getMessage()).contains("standInAuth.header");
        });
    }

    private static Throwable root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }
}
