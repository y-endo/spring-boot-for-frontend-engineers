package com.example.quickvote.poll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 投票の詳細（GET /api/polls/{pollId} などの応答）。
 * DB の行（Poll）をそのまま返さず、API として見せたい形に作り直している。
 *
 * @param status        OPEN / CLOSED。締切時刻を過ぎていれば CLOSED（その時点の判定結果）
 * @param role          呼び出した人の役割（HOST / MEMBER）
 * @param joinCode      参加コード。主催者にだけ返し、参加者には null
 * @param lastEventSeq  この応答を作った時点のイベント連番。SSE の取りこぼし判定の基準になる
 * @param myVoteOptionId 自分が投票した選択肢のID。未投票なら null
 */
public record PollDetail(
        UUID id,
        String title,
        String status,
        Instant closesAt,
        boolean allowMemberOptions,
        String role,
        int participantCount,
        String joinCode,
        long lastEventSeq,
        int totalVotes,
        Long myVoteOptionId,
        List<OptionView> options) {

    /** @param percent 得票率（%）。小数1桁で、全選択肢の合計がちょうど100.0（票が0件なら全部0.0） */
    public record OptionView(long id, String label, int votes, BigDecimal percent) {
    }
}
