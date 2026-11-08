package com.example.quickvote.poll;

import java.time.Instant;
import java.util.UUID;

/** polls テーブルの1行。 */
public record Poll(
        long id,
        UUID publicId,
        String title,
        PollStatus status,
        String joinCode,
        boolean allowMemberOptions,
        Instant closesAt,
        Instant deleteAfter,
        Instant createdAt,
        long lastEventSeq) {

    /**
     * 締切後か。締切時刻ちょうどは「締切後」として扱う（closes_at は含まない半開区間）。
     * 手動で締め切った場合も締切後。
     */
    public boolean isClosedAt(Instant now) {
        return status == PollStatus.CLOSED || !now.isBefore(closesAt);
    }
}
