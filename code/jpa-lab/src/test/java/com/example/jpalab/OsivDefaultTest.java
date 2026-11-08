package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.jpalab.support.AbstractLabTest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 第40章: open-in-view（OSIV）の既定値（true）。
 * 専用の ApplicationContext を作らせるため、中身に意味のないプロパティを1つ足している（起動時の警告ログを捕まえるため）。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "lab.context=osiv-default")
class OsivDefaultTest extends AbstractLabTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 起動時に警告ログが出る(CapturedOutput output) {
        List<String> warnings = output.getOut().lines()
                .filter(line -> line.contains("open-in-view"))
                .map(line -> line.substring(line.indexOf("WARN")))
                .toList();
        warnings.forEach(line -> System.out.println("[OSIV警告] " + line));
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("spring.jpa.open-in-view is enabled by default");
    }

    @Test
    void Controllerでの遅延ロードが_動いてしまう(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        var result = mvc.get().uri("/api/lab/polls/1/option-labels").exchange();

        System.out.println("[OSIV=true] ↓");
        labAndSqlLines(output).forEach(line -> System.out.println("  " + line));
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isEqualTo("[\"option-1\",\"option-2\",\"option-3\"]");
        List<String> lines = labAndSqlLines(output);
        // Service の SELECT → Controller のログ → Controller 内で options の SELECT、の順
        assertThat(lines.get(0)).startsWith("select p1_0.id");
        assertThat(lines.get(1)).contains("トランザクション中=false");
        assertThat(lines.get(2)).startsWith("select o1_0.poll_id");
    }

    @Test
    void トランザクションが終わっても_接続は返されない(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        mvc.get().uri("/api/lab/polls/1").exchange();

        String controllerLine = labAndSqlLines(output).stream().filter(l -> l.contains("controller:")).findFirst()
                .orElseThrow();
        System.out.println("[OSIV=true] " + controllerLine);
        assertThat(controllerLine).contains("トランザクション中=false").contains("使用中の接続数=1");
    }
}
