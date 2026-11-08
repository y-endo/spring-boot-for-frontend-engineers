package com.example.quickvote.poll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.vote.VoteRepository;

import org.springframework.stereotype.Service;

/**
 * 投票の作成・取得・選択肢の追加。
 * <p>
 * 完成形（code/quickvote）との差: トランザクションがない（第4部）、Idempotency-Key がない（第5部）、
 * イベント履歴がない（第7部）、締切の手動変更・削除がない。クラス名・メソッド名・エラーコードは同じ。
 * <p>
 * Service は Bean（アプリ全体で1つ）なので、フィールドに持つのは「依存する部品」と不変の値だけ。
 * リクエスト固有の値（visitorId など）は、必ずメソッドの引数で受け取る。
 */
@Service
public class PollService {

    /** 参加コードの衝突は31^8通り中の偶然の一致なのでまず起きない。念のための上限。 */
    private static final int MAX_JOIN_CODE_ATTEMPTS = 5;

    private final PollRepository polls;
    private final OptionRepository options;
    private final ParticipantRepository participants;
    private final ParticipantService access;
    private final VoteRepository votes;
    private final JoinCodeGenerator joinCodes;
    private final Clock clock;

    public PollService(PollRepository polls, OptionRepository options, ParticipantRepository participants,
            ParticipantService access, VoteRepository votes, JoinCodeGenerator joinCodes, Clock clock) {
        this.polls = polls;
        this.options = options;
        this.participants = participants;
        this.access = access;
        this.votes = votes;
        this.joinCodes = joinCodes;
        this.clock = clock;
    }

    /** 投票を作る。作った人が HOST になる。 */
    public PollDetail create(long visitorId, CreatePollRequest request) {
        Instant now = clock.instant();
        Instant closesAt = now.plus(request.closesInMinutes(), ChronoUnit.MINUTES); // 締切の時刻はサーバーが決める
        for (int attempt = 1; attempt <= MAX_JOIN_CODE_ATTEMPTS; attempt++) {
            try {
                return createOnce(visitorId, request, joinCodes.generate(), closesAt, now);
            } catch (PollRepository.DuplicateJoinCodeException e) {
                // 別のコードで取り直す
            }
        }
        throw new IllegalStateException("参加コードを生成できませんでした");
    }

    private PollDetail createOnce(long visitorId, CreatePollRequest request, String joinCode, Instant closesAt,
            Instant now) {
        long pollId = polls.insert(UUID.randomUUID(), request.title(), joinCode, request.allowMemberOptions(),
                closesAt, now);
        long participantId = participants.insert(pollId, visitorId, ParticipantRole.HOST, now);
        for (String label : request.options()) {
            options.insert(pollId, label, participantId, now);
        }
        Poll poll = polls.findById(pollId).orElseThrow();
        return loadDetail(new ParticipantContext(pollId, poll.publicId(), participantId, ParticipantRole.HOST), now);
    }

    public PollDetail get(long visitorId, UUID pollPublicId) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        return loadDetail(context, clock.instant());
    }

    private PollDetail loadDetail(ParticipantContext context, Instant now) {
        Poll poll = polls.findById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        List<OptionRepository.OptionCount> counts = options.findWithVoteCounts(poll.id());
        List<BigDecimal> shares = VoteShareCalculator
                .calculate(counts.stream().map(OptionRepository.OptionCount::votes).toList());
        List<PollDetail.OptionView> views = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            OptionRepository.OptionCount c = counts.get(i);
            views.add(new PollDetail.OptionView(c.id(), c.label(), c.votes(), shares.get(i)));
        }
        int totalVotes = counts.stream().mapToInt(OptionRepository.OptionCount::votes).sum();
        Long myVote = votes.findOptionId(poll.id(), context.participantId()).orElse(null);
        return new PollDetail(
                poll.publicId(),
                poll.title(),
                poll.isClosedAt(now) ? PollStatus.CLOSED.name() : PollStatus.OPEN.name(),
                poll.closesAt(),
                poll.allowMemberOptions(),
                context.role().name(),
                participants.countActive(poll.id()),
                context.isHost() ? poll.joinCode() : null,
                poll.lastEventSeq(),
                totalVotes,
                myVote,
                views);
    }

    /**
     * 選択肢の追加。HOST、または allow_member_options が true のときの MEMBER。
     * <p>
     * この時点の保証: 上限(10)の判定は「数える → 足す」の2操作で、その間は守られていない。
     * 9件のときに2人が同時に追加すると11件になりうる（第4部で直す）。
     */
    public OptionResponse addOption(long visitorId, UUID pollPublicId, String label) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        Poll poll = polls.findById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        if (!context.isHost() && !poll.allowMemberOptions()) {
            throw new ApiException(ErrorCode.MEMBER_OPTIONS_DISABLED);
        }
        Instant now = clock.instant();
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        if (options.count(poll.id()) >= PollRules.MAX_OPTIONS) {
            throw new ApiException(ErrorCode.OPTION_LIMIT_REACHED);
        }
        long optionId = options.insert(poll.id(), label, context.participantId(), now);
        return new OptionResponse(optionId, label);
    }
}
