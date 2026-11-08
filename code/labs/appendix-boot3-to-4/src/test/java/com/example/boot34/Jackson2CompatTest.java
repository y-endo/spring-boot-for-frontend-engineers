package com.example.boot34;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.databind.json.JsonMapper;

// spring.jackson.use-jackson2-defaults=true で、既定値が Jackson 2 寄りに戻る（移行ガイドの記述を実測）
@JsonTest(properties = "spring.jackson.use-jackson2-defaults=true")
class Jackson2CompatTest {

    record Plain(String name) {}
    record Prim(boolean flag, int n) {}

    @Autowired JsonMapper mapper;

    @Test
    void 観察する() throws Exception {
        System.out.println("## use-jackson2-defaults=true");
        System.out.println("FAIL_ON_UNKNOWN_PROPERTIES = " + mapper.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        System.out.println("FAIL_ON_NULL_FOR_PRIMITIVES = " + mapper.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        System.out.println("SORT_PROPERTIES_ALPHABETICALLY = " + mapper.isEnabled(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        System.out.println("WRITE_DATES_AS_TIMESTAMPS = " + mapper.isEnabled(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS));
        try { mapper.readValue("{\"name\":\"x\",\"extra\":1}", Plain.class); System.out.println("unknown   : accepted"); }
        catch (Exception e) { System.out.println("unknown   : rejected"); }
        try { mapper.readValue("{\"flag\":null,\"n\":1}", Prim.class); System.out.println("null->prim: accepted"); }
        catch (Exception e) { System.out.println("null->prim: rejected"); }
        System.out.println("time      : " + mapper.writeValueAsString(java.time.Instant.parse("2026-11-08T03:04:05Z")));
    }
}
