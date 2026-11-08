package com.example.review3.bad;

import java.time.OffsetDateTime;

/** polls テーブルの1行。 */
public record PollRow(
        Long id,
        String title,
        String joinCode,
        Long hostVisitorId,
        Long version,
        OffsetDateTime deleteAfter,
        OffsetDateTime createdAt) {
}
