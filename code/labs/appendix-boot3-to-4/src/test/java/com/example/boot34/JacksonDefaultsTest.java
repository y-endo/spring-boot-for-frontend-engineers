package com.example.boot34;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Jackson 3 の既定値を、素の JsonMapper と Spring Boot が組み立てた JsonMapper の両方で観察する
@JsonTest
class JacksonDefaultsTest {

    record Ordered(String zebra, String apple, int mango) {}
    record Timed(Instant at, Duration took) {}
    record Prim(boolean flag, int n) {}
    record Plain(String name) {}

    static class PojoIn { public String zebra; }

    static class Pojo {                       // record ではない普通のクラス（getter の並びは b, a）
        public String getZebra() { return "z"; }
        public String getApple() { return "a"; }
    }

    @Autowired JsonMapper boot;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext ctx;

    void observe(String label, JsonMapper m) throws Exception {
        System.out.println("## " + label);
        System.out.println("FAIL_ON_UNKNOWN_PROPERTIES = " + m.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        System.out.println("FAIL_ON_NULL_FOR_PRIMITIVES = " + m.isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        System.out.println("SORT_PROPERTIES_ALPHABETICALLY = " + m.isEnabled(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        System.out.println("WRITE_DATES_AS_TIMESTAMPS = " + m.isEnabled(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS));
        try { m.readValue("{\"zebra\":\"z\",\"nope\":1}", PojoIn.class); System.out.println("pojo unknown: accepted"); }
        catch (JacksonException e) { System.out.println("pojo unknown: rejected"); }
        System.out.println("order     : " + m.writeValueAsString(new Ordered("z", "a", 1)));
        System.out.println("time      : " + m.writeValueAsString(new Timed(Instant.parse("2026-11-08T03:04:05Z"), Duration.ofSeconds(90))));
        System.out.println("pojo order: " + m.writeValueAsString(new Pojo()));
        var hm = new java.util.HashMap<String, Integer>();
        hm.put("b", 1); hm.put("a", 2); hm.put("c", 3);
        System.out.println("map order : " + m.writeValueAsString(hm));
        try { m.readValue("{\"name\":\"x\",\"extra\":1}", Plain.class); System.out.println("unknown   : accepted"); }
        catch (JacksonException e) { System.out.println("unknown   : rejected (" + e.getClass().getSimpleName() + ")"); }
        try { m.readValue("{\"flag\":null,\"n\":1}", Prim.class); System.out.println("null->prim: accepted"); }
        catch (JacksonException e) { System.out.println("null->prim: rejected (" + e.getClass().getSimpleName() + ")"); }
        try { m.readValue("{\"n\":1}", Prim.class); System.out.println("missing   : accepted"); }
        catch (JacksonException e) { System.out.println("missing   : rejected (" + e.getClass().getSimpleName() + ")"); }
        try { m.readValue("{\"name\":\"x\"} trailing", Plain.class); System.out.println("trailing  : accepted"); }
        catch (JacksonException e) { System.out.println("trailing  : rejected (" + e.getClass().getSimpleName() + ")"); }
        System.out.println("checked?  : JacksonException is RuntimeException = " + (RuntimeException.class.isAssignableFrom(JacksonException.class)));
    }

    @Test
    void 既定値を観察する() throws Exception {
        observe("素の JsonMapper.builder().build()", JsonMapper.builder().build());
        observe("Spring Boot が構成した JsonMapper", boot);
    }
}
