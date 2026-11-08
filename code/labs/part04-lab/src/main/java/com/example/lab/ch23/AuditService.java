package com.example.lab.ch23;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 伝播(propagation)の実験。呼ばれる側。 */
@Service
public class AuditService {

    private final LabRepository repo;

    public AuditService(LabRepository repo) {
        this.repo = repo;
    }

    /** 既定の REQUIRED: 呼び出し元のトランザクションがあれば、それに参加する。 */
    @Transactional
    public String recordRequired(String message) {
        repo.insertAudit(message);
        return "audit(REQUIRED) pid=" + repo.backendPid() + " " + TxProbe.state();
    }

    /** REQUIRES_NEW: 呼び出し元を一時停止して、別のトランザクション（別の接続）で実行し、先にコミットする。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordRequiresNew(String message) {
        repo.insertAudit(message);
        return "audit(REQUIRES_NEW) pid=" + repo.backendPid() + " " + TxProbe.state();
    }

    /** REQUIRED の内側で RuntimeException。呼び出し元のトランザクションに「ロールバック専用」の印が付く。 */
    @Transactional
    public void failRequired() {
        throw new IllegalStateException("内側(REQUIRED)で失敗");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRequiresNew() {
        throw new IllegalStateException("内側(REQUIRES_NEW)で失敗");
    }
}
