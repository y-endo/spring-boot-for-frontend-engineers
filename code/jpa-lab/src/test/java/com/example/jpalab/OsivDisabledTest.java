package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.jpalab.support.AbstractLabTest;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 第40章: spring.jpa.open-in-view=false にすると、同じコードが LazyInitializationException で落ちる。 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class OsivDisabledTest extends AbstractLabTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void 同じControllerが_LazyInitializationException_で落ちる() {
        seed(1, 3, 0);

        var result = mvc.get().uri("/api/lab/polls/1/option-labels").exchange();

        assertThat(result).failure().hasRootCauseInstanceOf(LazyInitializationException.class);
        System.out.println("[OSIV=false] " + result.getUnresolvedException().getClass().getSimpleName() + " ← "
                + rootCause(result.getUnresolvedException()).getMessage());
    }

    @Test
    void DTOまで作って返すControllerは_動き_接続も返されている(CapturedOutput output) {
        seed(1, 3, 0);
        mark(output);

        var result = mvc.get().uri("/api/lab/polls/1").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson()
                .isEqualTo("{\"id\":1,\"title\":\"poll-1\",\"optionLabels\":[\"option-1\",\"option-2\",\"option-3\"]}");
        String controllerLine = labAndSqlLines(output).stream().filter(l -> l.contains("controller:")).findFirst()
                .orElseThrow();
        System.out.println("[OSIV=false] " + controllerLine);
        assertThat(controllerLine).contains("トランザクション中=false").contains("使用中の接続数=0");
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
