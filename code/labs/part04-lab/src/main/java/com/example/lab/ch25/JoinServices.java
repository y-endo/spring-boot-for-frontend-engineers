package com.example.lab.ch25;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加の3つの書き方。「人数が50人未満なら参加者を1行足す」を、守りの強さを変えて並べる。
 * 本体は QuickVote の JoinService.join と同じ流れ（数える → 上限判定 → 挿入）。
 */
public final class JoinServices {

    /** 参加者の上限（QuickVote の PollRules.MAX_PARTICIPANTS と同じ）。 */
    public static final int MAX_PARTICIPANTS = 50;

    private JoinServices() {
    }

    /** ① 第3部の終わりの形: トランザクションもロックもない。数える → 判定 → 挿入。 */
    @Service
    public static class Unsafe {

        private final LabRepository repo;
        private final RaceWindow window;

        public Unsafe(LabRepository repo, RaceWindow window) {
            this.repo = repo;
            this.window = window;
        }

        public void join(long pollId, long visitorId) {
            int current = repo.countParticipants(pollId);          // (1) 数える
            if (current >= MAX_PARTICIPANTS) {
                throw new PollFullException(pollId);               // (2) 判定する
            }
            window.pass();                                         // （実験用の隙間）
            repo.insertParticipant(pollId, visitorId, "MEMBER");   // (3) 挿入する
        }
    }

    /** ② @Transactional を付けただけ。ロックがない。 */
    @Service
    public static class TransactionOnly {

        private final LabRepository repo;
        private final RaceWindow window;

        public TransactionOnly(LabRepository repo, RaceWindow window) {
            this.repo = repo;
            this.window = window;
        }

        @Transactional
        public void join(long pollId, long visitorId) {
            int current = repo.countParticipants(pollId);
            if (current >= MAX_PARTICIPANTS) {
                throw new PollFullException(pollId);
            }
            window.pass();
            repo.insertParticipant(pollId, visitorId, "MEMBER");
        }
    }

    /** ③ 親行（polls）を FOR UPDATE でロックしてから数える。QuickVote 本体と同じ規則。 */
    @Service
    public static class Locked {

        private final LabRepository repo;
        private final RaceWindow window;

        public Locked(LabRepository repo, RaceWindow window) {
            this.repo = repo;
            this.window = window;
        }

        @Transactional
        public void join(long pollId, long visitorId) {
            repo.lockPoll(pollId).orElseThrow();                   // (1) 先に鍵を掛ける
            int current = repo.countParticipants(pollId);          // (2) 鍵を持ったまま数える
            if (current >= MAX_PARTICIPANTS) {
                throw new PollFullException(pollId);
            }
            window.pass();
            repo.insertParticipant(pollId, visitorId, "MEMBER");
        }
    }

    /**
     * ④ 「別の更新経路」。招待の受諾のように、参加者を増やす別の入口。ここでロックを忘れると、
     * ③ が正しくても上限は守れない（すべての更新経路が同じ規則に従う必要がある）。
     */
    @Service
    public static class InviteAcceptWithoutLock {

        private final LabRepository repo;
        private final RaceWindow window;

        public InviteAcceptWithoutLock(LabRepository repo, RaceWindow window) {
            this.repo = repo;
            this.window = window;
        }

        @Transactional
        public void accept(long pollId, long visitorId) {
            int current = repo.countParticipants(pollId);          // ロックしていない
            if (current >= MAX_PARTICIPANTS) {
                throw new PollFullException(pollId);
            }
            window.pass();
            repo.insertParticipant(pollId, visitorId, "HOST");
        }
    }
}
