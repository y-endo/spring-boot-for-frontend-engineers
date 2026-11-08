package com.example.quickvote.event;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.example.quickvote.common.Db;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** イベントの保存と履歴の取得。 */
@Service
public class PollEventService {

    /** 履歴を1回の DB 読み出しで返す最大件数（1ページ）。これを超えて遅れている接続には、配信側が続きのページを読み進める。 */
    static final int PAGE_SIZE = 500;

    private final PollEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final ObjectProvider<PollEventHub> hub; // [lab] 循環参照を避けるため遅延取得

    public PollEventService(PollEventRepository repository, JsonMapper jsonMapper, Clock clock,
            ObjectProvider<PollEventHub> hub) {
        this.hub = hub;
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * 業務更新と同じトランザクションの中で呼ぶ（MANDATORY で強制）。
     * 呼び出し側は先に polls 行を FOR UPDATE でロックしておくこと（採番の前提）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(long pollId, String type, Map<String, Object> payload) {
        long seq = repository.nextSeq(pollId);
        repository.insert(pollId, seq, type, jsonMapper.writeValueAsString(payload), Db.now(clock));
        repository.notifyPoll(pollId);
        // [lab] コミット後に、このJVMの購読者だけへ直接知らせる（メモリ配信のみ）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                hub.getObject().onNotify(pollId);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<PollEvent> findAfter(long pollId, long afterSeq) {
        return repository.findAfter(pollId, afterSeq, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public long lastSeq(long pollId) {
        return repository.lastSeq(pollId);
    }
}
