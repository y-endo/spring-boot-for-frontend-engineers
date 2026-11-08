package com.example.review3.infra;

/** SSE の購読者名簿への一斉送信。bad と fixed が実装する（テストは同じ窓口から確かめる）。 */
public interface Broadcaster {

    void broadcast(String data);

    int subscriberCount();
}
