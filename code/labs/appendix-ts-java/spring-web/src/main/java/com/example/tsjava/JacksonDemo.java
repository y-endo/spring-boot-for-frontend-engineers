package com.example.tsjava;

import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// JSON.stringify / JSON.parse に当たるもの（Jackson 3：パッケージは tools.jackson）
class JacksonDemo {

    record Dto(String title, int votes) {}

    static String run() {
        JsonMapper mapper = JsonMapper.builder().build();             // Spring Boot では自動構成された Bean を注入する

        String json = mapper.writeValueAsString(new Dto("ランチ", 3));        // JSON.stringify(obj)
        Dto back = mapper.readValue(json, Dto.class);                   // JSON.parse(text) as Dto（検証はしない）
        List<Dto> list = mapper.readValue("[{\"title\":\"a\",\"votes\":1}]", new TypeReference<List<Dto>>() {});
        return json + " / " + back + " / " + list;
    }
}
