package com.example.quickvote.participant;

import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;

import org.springframework.stereotype.Service;

/**
 * 認可の入口。「この訪問者は、この投票の参加者か？」を確かめる。
 * 認可のチェックをここ1か所に集め、各サービスは require() を呼んでから処理を始める。
 */
@Service
public class ParticipantService {

    private final ParticipantRepository participants;

    public ParticipantService(ParticipantRepository participants) {
        this.participants = participants;
    }

    /** 参加者でなければ404。投票が存在しない場合と区別しない（存在を漏らさない）。 */
    public ParticipantContext require(UUID pollPublicId, long visitorId) {
        return participants.findActive(pollPublicId, visitorId)
                .orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
    }

    /** 主催者でなければ403。参加者であることは確認済みなので、存在を隠す必要はない。 */
    public ParticipantContext requireHost(UUID pollPublicId, long visitorId) {
        ParticipantContext context = require(pollPublicId, visitorId);
        if (!context.isHost()) {
            throw new ApiException(ErrorCode.HOST_ONLY);
        }
        return context;
    }
}
