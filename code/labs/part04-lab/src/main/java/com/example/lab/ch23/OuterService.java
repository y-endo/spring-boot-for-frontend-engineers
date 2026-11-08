package com.example.lab.ch23;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 伝播(propagation)の実験。呼ぶ側。AuditService は別の Bean なので、呼び出しはプロキシを通る。 */
@Service
public class OuterService {

    private final LabRepository repo;
    private final AuditService audit;

    public OuterService(LabRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** 業務(INSERT polls) → 監査ログ → 業務が失敗。監査ログも一緒に消えるか？ */
    @Transactional
    public void businessThenFailWithRequired(String joinCode) {
        repo.insertPoll("REQUIRED監査", joinCode);
        System.out.println("  外側: pid=" + repo.backendPid() + " " + TxProbe.state());
        System.out.println("  内側: " + audit.recordRequired("REQUIREDの監査ログ"));
        throw new IllegalStateException("業務が失敗");
    }

    @Transactional
    public void businessThenFailWithRequiresNew(String joinCode) {
        repo.insertPoll("REQUIRES_NEW監査", joinCode);
        System.out.println("  外側: pid=" + repo.backendPid() + " " + TxProbe.state());
        System.out.println("  内側: " + audit.recordRequiresNew("REQUIRES_NEWの監査ログ"));
        throw new IllegalStateException("業務が失敗");
    }

    /** 内側(REQUIRED)が失敗し、外側が catch して続行。外側は成功したつもりで COMMIT しようとする。 */
    @Transactional
    public void catchInnerRequired(String joinCode) {
        repo.insertPoll("内側REQUIRED失敗", joinCode);
        try {
            audit.failRequired();
        } catch (IllegalStateException e) {
            System.out.println("  外側が catch して続行: " + e.getMessage());
        }
    }

    /** 内側(REQUIRES_NEW)が失敗し、外側が catch して続行。内側だけがロールバックされる。 */
    @Transactional
    public void catchInnerRequiresNew(String joinCode) {
        repo.insertPoll("内側REQUIRES_NEW失敗", joinCode);
        try {
            audit.failRequiresNew();
        } catch (IllegalStateException e) {
            System.out.println("  外側が catch して続行: " + e.getMessage());
        }
    }
}
