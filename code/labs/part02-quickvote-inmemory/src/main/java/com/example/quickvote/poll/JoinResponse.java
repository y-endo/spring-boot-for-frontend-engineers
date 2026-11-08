package com.example.quickvote.poll;

import java.util.UUID;

import com.example.quickvote.participant.ParticipantRole;

/** 参加（参加コード・招待）の結果。以降は pollId で API を呼ぶ。 */
public record JoinResponse(UUID pollId, ParticipantRole role) {
}
