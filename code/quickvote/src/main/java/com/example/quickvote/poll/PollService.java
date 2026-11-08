package com.example.quickvote.poll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.example.quickvote.common.ApiException;
import com.example.quickvote.common.Db;
import com.example.quickvote.common.ErrorCode;
import com.example.quickvote.common.IdempotencyService;
import com.example.quickvote.common.Tokens;
import com.example.quickvote.event.PollEventService;
import com.example.quickvote.participant.ParticipantContext;
import com.example.quickvote.participant.ParticipantRepository;
import com.example.quickvote.participant.ParticipantRole;
import com.example.quickvote.participant.ParticipantService;
import com.example.quickvote.vote.VoteRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PollService {

    /** 参加コードの衝突は31^8通り中の偶然の一致なのでまず起きない。念のための上限。 */
    private static final int MAX_JOIN_CODE_ATTEMPTS = 5;

    private final PollRepository polls;
    private final OptionRepository options;
    private final ParticipantRepository participants;
    private final ParticipantService access;
    private final VoteRepository votes;
    private final PollEventService events;
    private final IdempotencyService idempotency;
    private final JoinCodeGenerator joinCodes;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PollService(PollRepository polls, OptionRepository options, ParticipantRepository participants,
            ParticipantService access, VoteRepository votes, PollEventService events, IdempotencyService idempotency,
            JoinCodeGenerator joinCodes, TransactionTemplate transaction, Clock clock) {
        this.polls = polls;
        this.options = options;
        this.participants = participants;
        this.access = access;
        this.votes = votes;
        this.events = events;
        this.idempotency = idempotency;
        this.joinCodes = joinCodes;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * 投票を作る。作った人が HOST になる。
     * <p>
     * @Transactional ではなく TransactionTemplate を使う理由: 参加コードが重複すると UNIQUE 制約違反になり、
     * PostgreSQL ではそのトランザクションは以降のSQLを受け付けない（aborted 状態）。
     * 取り直すには「新しいトランザクション」が必要なので、1回ぶんの処理を1トランザクションにして外側でリトライしている。
     */
    public PollDetail create(long visitorId, CreatePollRequest request) {
        Instant now = Db.now(clock);
        // 締切は「今から何分後」。サーバーの Clock から計算するので、クライアントの時計や通信時間に左右されない。
        Instant closesAt = now.plus(Duration.ofMinutes(request.closesInMinutes()));
        for (int attempt = 1; attempt <= MAX_JOIN_CODE_ATTEMPTS; attempt++) {
            String joinCode = joinCodes.generate();
            try {
                return Objects.requireNonNull(
                        transaction.execute(status -> createOnce(visitorId, request, joinCode, closesAt, now)));
            } catch (DuplicateKeyException e) {
                // join_code の UNIQUE 違反。別のコードで取り直す（ログには出さない: メッセージにコードが含まれる）。
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

    /**
     * 詳細の取得。複数のSELECT（投票・選択肢・票・人数）が同じ時点の状態を見るように REPEATABLE READ にする。
     * READ COMMITTED（既定）だと、SELECT の間に他人の投票がコミットされ、票数と合計がずれて見えることがある。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PollDetail get(long visitorId, UUID pollPublicId) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        return loadDetail(context, Db.now(clock));
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
     * 上限(10)の判定は「polls 行をロック → 再カウント → 挿入」。ロックなしで count してから insert すると、
     * 9件のときに2人が同時に「まだ入る」と判断して11件になる。
     */
    @Transactional
    public IdempotencyService.Result<OptionResponse> addOption(long visitorId, UUID pollPublicId,
            String idempotencyKey, String label) {
        ParticipantContext context = access.require(pollPublicId, visitorId);
        if (!context.isHost()) {
            Poll poll = polls.findById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
            if (!poll.allowMemberOptions()) {
                throw new ApiException(ErrorCode.MEMBER_OPTIONS_DISABLED);
            }
        }
        // 「同じキーで同じ内容」かを判定するためのハッシュ。投票ID＋ラベルが同じなら同じリクエストとみなす。
        String requestHash = Tokens.sha256Hex(pollPublicId + "\n" + label);
        return idempotency.execute(visitorId, idempotencyKey, requestHash, 201, OptionResponse.class,
                () -> insertOption(context, label));
    }

    private OptionResponse insertOption(ParticipantContext context, String label) {
        Poll poll = polls.lockById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        Instant now = Db.now(clock);
        if (poll.isClosedAt(now)) {
            throw new ApiException(ErrorCode.POLL_CLOSED);
        }
        if (options.count(poll.id()) >= PollRules.MAX_OPTIONS) {
            throw new ApiException(ErrorCode.OPTION_LIMIT_REACHED);
        }
        long optionId = options.insert(poll.id(), label, context.participantId(), now);
        events.append(poll.id(), "OPTION_ADDED", Map.of("optionId", optionId));
        return new OptionResponse(optionId, label);
    }

    /** 締切を早める（HOSTのみ）。すでに締切後なら何もしない（再送しても結果は同じ）。 */
    @Transactional
    public void close(long visitorId, UUID pollPublicId) {
        ParticipantContext context = access.requireHost(pollPublicId, visitorId);
        Poll poll = polls.lockById(context.pollId()).orElseThrow(() -> new ApiException(ErrorCode.POLL_NOT_FOUND));
        Instant now = Db.now(clock);
        if (poll.isClosedAt(now)) {
            return;
        }
        polls.close(poll.id(), now);
        events.append(poll.id(), "POLL_CLOSED", Map.of());
    }

    /** 投票の削除（HOSTのみ）。選択肢・参加者・票・招待・イベントは外部キーの CASCADE で一緒に消える。 */
    @Transactional
    public void delete(long visitorId, UUID pollPublicId) {
        ParticipantContext context = access.requireHost(pollPublicId, visitorId);
        polls.delete(context.pollId());
    }
}
