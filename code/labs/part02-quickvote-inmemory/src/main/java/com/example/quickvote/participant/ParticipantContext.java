package com.example.quickvote.participant;

import java.util.UUID;

/**
 * 「この訪問者は、この投票の、この参加者である」という認可済みの事実。
 * 権限判定を通ったあとのサービス処理には、生のIDではなくこれを渡す。
 */
public record ParticipantContext(long pollId, UUID pollPublicId, long participantId, ParticipantRole role) {

    public boolean isHost() {
        return role == ParticipantRole.HOST;
    }
}
