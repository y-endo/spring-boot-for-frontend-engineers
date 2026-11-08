package com.example.tsjava;

import org.springframework.web.client.RestClient;

// サーバー側から別のAPIを呼ぶ（fetch / axios に当たる）。コンパイル確認のみ（外部通信はしない）。
class RestClientDemo {

    record Todo(long id, String title) {}

    // const res = await fetch(`${base}/todos/1`, { headers: { Accept: "application/json" } }); return await res.json()
    Todo fetchTodo(RestClient client) {
        return client.get()
                .uri("/todos/{id}", 1)
                .header("Accept", "application/json")
                .retrieve()                      // 4xx/5xx は例外になる（fetch と違い res.ok の確認が要らない）
                .body(Todo.class);
    }

    RestClient create(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
