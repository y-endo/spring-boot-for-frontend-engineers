package com.example.lab.ch23;

import com.example.lab.LabRepository;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 例外を握りつぶすと、プロキシ（秘書）は失敗に気づけない。 */
@Service
public class SwallowService {

    private final LabRepository repo;

    public SwallowService(LabRepository repo) {
        this.repo = repo;
    }

    /** アプリ側の失敗を catch して握りつぶす。2本目のINSERTは実行されないまま、COMMIT される。 */
    @Transactional
    public void swallowAppFailure(String joinCode, long hostVisitorId) {
        long pollId = repo.insertPoll("握りつぶし(アプリの失敗)", joinCode);
        try {
            riskyStep();
            repo.insertParticipant(pollId, hostVisitorId, "HOST");
        } catch (RuntimeException e) {
            System.out.println("  catch して続行: " + e.getMessage());
        }
    }

    /** DB のエラー（存在しない visitor への外部キー違反）を catch して握りつぶす。PostgreSQL はこのトランザクションを使えなくする。 */
    @Transactional
    public void swallowDbFailure(String joinCode) {
        long pollId = repo.insertPoll("握りつぶし(DBのエラー)", joinCode);
        try {
            repo.insertParticipant(pollId, 999_999L, "HOST");   // visitors に無い → FK違反
        } catch (DataAccessException e) {
            System.out.println("  catch して続行: " + e.getClass().getSimpleName());
        }
    }

    private void riskyStep() {
        throw new IllegalStateException("riskyStep が失敗");
    }
}
