package com.example.quickvote.event;

import java.time.Instant;

/**
 * poll_events の1行。seq は投票ごとの連番で、SSE の id: にそのまま使う。
 * payload は JSON 文字列（選択肢のIDなど。ラベルなどの本文は入れない）。
 */
public record PollEvent(long seq, String type, String payload, Instant createdAt) {
}
