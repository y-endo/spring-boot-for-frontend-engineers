package com.example.review1.fixed;

import java.io.IOException;

import com.example.review1.infra.ArchiveWriter;
import com.example.review1.infra.EventRecorder;
import com.example.review1.infra.PollMembers;
import com.example.review1.infra.WebhookClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PollMemberService implements PollMembers {

    private static final Logger log = LoggerFactory.getLogger(PollMemberService.class);
    private static final int MAX_PARTICIPANTS = 50;

    private final MemberRepository members;
    private final WebhookClient webhook;
    private final EventRecorder events;
    private final ArchiveWriter archive;
    private final TransactionTemplate tx;

    public PollMemberService(MemberRepository members, WebhookClient webhook, EventRecorder events,
            ArchiveWriter archive, TransactionTemplate tx) {
        this.members = members;
        this.webhook = webhook;
        this.events = events;
        this.archive = archive;
        this.tx = tx;
    }

    /** トランザクションは「DBを変える部分」だけ。外部通知はコミットのあとに、トランザクションの外で行う。 */
    @Override
    public void join(long pollId, long visitorId) {
        tx.executeWithoutResult(status -> joinInTransaction(pollId, visitorId));
        try {
            webhook.notifyJoined(pollId, visitorId);
        } catch (RuntimeException e) {
            // 参加はすでに確定している。通知の失敗は参加の失敗ではない（再送・ジョブ化は別途設計する）。
            log.warn("参加の通知に失敗しました pollId={}", pollId, e);
        }
    }

    private void joinInTransaction(long pollId, long visitorId) {
        members.lockPoll(pollId);                       // 先に親行をロックしてから数える
        int current = members.countParticipants(pollId);
        if (current >= MAX_PARTICIPANTS) {
            throw new IllegalStateException("参加者が上限に達しています");
        }
        members.insertParticipant(pollId, visitorId);
    }

    /** イベントの記録に失敗したら、選択肢の追加ごと取り消す（握りつぶさない）。 */
    @Override
    @Transactional
    public void addOption(long pollId, String label) {
        members.insertOption(pollId, label);
        events.record(pollId, "OPTION_ADDED");
    }

    /** 検査例外でもロールバックする。「呼び出し側にエラーを返したなら、DBも変わっていない」を守る。 */
    @Override
    @Transactional(rollbackFor = IOException.class)
    public void close(long pollId) throws IOException {
        members.markClosed(pollId);
        archive.writeSnapshot(pollId);
    }
}
