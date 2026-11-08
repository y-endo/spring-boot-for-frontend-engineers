package com.example.jpalab.poll;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollService {

    /** 実験の区切りをログに出すためのロガー。SQL のログの間に「いま何をしたか」の目印を挟む。 */
    private static final Logger lab = LoggerFactory.getLogger("lab");

    private final PollRepository polls;

    public PollService(PollRepository polls) {
        this.polls = polls;
    }

    // ---------------------------------------------------------------- 第39章: 永続コンテキストと dirty checking

    @Transactional
    public Long create(String title, Instant closesAt, String... labels) {
        Poll poll = new Poll(title, closesAt);
        for (String label : labels) {
            poll.addOption(label);
        }
        lab.info("-- save() を呼ぶ直前");
        polls.save(poll);
        lab.info("-- save() が戻った直後（IDENTITY なので INSERT はここまでに出ている）");
        return poll.getId();
    }

    /** save() を呼ばない。それでも UPDATE が出る（dirty checking）。 */
    @Transactional
    public void rename(Long id, String newTitle) {
        Poll poll = polls.findById(id).orElseThrow();
        lab.info("-- setter を呼ぶ直前");
        poll.rename(newTitle);
        lab.info("-- setter を呼んだ直後（まだ UPDATE は出ていない）。メソッドを抜けるとコミット");
    }

    /** @Transactional がない。変更は DB に届かない（黙って捨てられる）。 */
    public void renameWithoutTransaction(Long id, String newTitle) {
        Poll poll = polls.findById(id).orElseThrow();
        poll.rename(newTitle);
    }

    /** 同じトランザクションで同じ id を2回読む。2回目は SQL を出さず、同じオブジェクトが返る。 */
    @Transactional(readOnly = true)
    public boolean readTwiceIsSameInstance(Long id) {
        Poll first = polls.findById(id).orElseThrow();
        lab.info("-- 2回目の findById");
        Poll second = polls.findById(id).orElseThrow();
        return first == second;
    }

    /** 変更したあとで JPQL を実行する。Hibernate は「クエリの結果が古くならないよう」先に UPDATE を出す（auto flush）。 */
    @Transactional
    public long renameThenCount(Long id, String newTitle) {
        Poll poll = polls.findById(id).orElseThrow();
        poll.rename(newTitle);
        lab.info("-- 変更後に count クエリを実行");
        return polls.countByTitle(newTitle);
    }

    @Transactional
    public void close(Long id, Instant now) {
        polls.findById(id).orElseThrow().close(now);
    }

    @Transactional
    public void delete(Long id) {
        Poll poll = polls.findById(id).orElseThrow();
        lab.info("-- delete() を呼ぶ直前");
        polls.delete(poll);
    }

    // ---------------------------------------------------------------- 第40章: 一覧の作り方5通り

    /** 遅延ロード任せ。投票が N 件なら、SQL は 1 + N 本になる。 */
    @Transactional(readOnly = true)
    public List<PollView> listLazy() {
        return polls.findAllByOrderByIdAsc().stream()
                .map(p -> new PollView(p.getId(), p.getTitle(), labelsOf(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PollView> listFetchJoin() {
        return polls.findAllWithOptions().stream()
                .map(p -> new PollView(p.getId(), p.getTitle(), labelsOf(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PollView> listEntityGraph() {
        return polls.findAllWithGraphByOrderByIdAsc().stream()
                .map(p -> new PollView(p.getId(), p.getTitle(), labelsOf(p)))
                .toList();
    }

    /** Entity を経由せず、必要な列だけを DTO で受け取る。Java 側でグループ化する。 */
    @Transactional(readOnly = true)
    public List<PollView> listDto() {
        Map<Long, String> titles = new LinkedHashMap<>();
        Map<Long, List<String>> labels = new LinkedHashMap<>();
        for (PollOptionRow row : polls.findOptionRows()) {
            titles.putIfAbsent(row.pollId(), row.title());
            labels.computeIfAbsent(row.pollId(), k -> new java.util.ArrayList<>()).add(row.label());
        }
        return titles.entrySet().stream()
                .map(e -> new PollView(e.getKey(), e.getValue(), labels.get(e.getKey())))
                .toList();
    }

    private static List<String> labelsOf(Poll poll) {
        return poll.getOptions().stream().map(Option::getLabel).toList();
    }

    /** トランザクションは、このメソッドを抜けた時点で終わる。返ってくる Poll の options は未取得のまま。 */
    @Transactional(readOnly = true)
    public Poll findEntity(Long id) {
        return polls.findById(id).orElseThrow();
    }

    /** 安全な版: トランザクションの中で、必要な分を DTO に詰めてから返す。 */
    @Transactional(readOnly = true)
    public PollView findView(Long id) {
        Poll poll = polls.findById(id).orElseThrow();
        return new PollView(poll.getId(), poll.getTitle(), labelsOf(poll));
    }
}
