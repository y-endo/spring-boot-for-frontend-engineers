package com.example.lab.ch24;

import com.example.lab.LabRepository;
import com.example.lab.ch23.AuditService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** コネクションプールの実験。コネクションを「どれだけ長く握るか」だけが違う処理を並べる。 */
@Service
public class PoolDemoService {

    private final JdbcClient jdbc;
    private final LabRepository repo;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public PoolDemoService(JdbcClient jdbc, LabRepository repo, AuditService audit,
            TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.repo = repo;
        this.audit = audit;
        this.transaction = transaction;
    }

    /** 遅いSQL（pg_sleep）。実行している間、コネクションを1本握る。トランザクションの指定はない。 */
    public String slowQuery(double seconds) {
        jdbc.sql("SELECT pg_sleep(:s)").param("s", seconds).query().singleValue();
        return "slow done";
    }

    /** 速いSQL。コネクションを一瞬だけ使う。 */
    public String fastQuery() {
        Integer one = jdbc.sql("SELECT 1").query(Integer.class).single();
        return "fast done " + one;
    }

    /**
     * 悪い例: トランザクションの中で外部通信（ここでは sleep で代用）をする。
     * DBは何もしていないのに、トランザクションが終わるまでコネクションを握り続ける。
     */
    @Transactional
    public String externalCallInsideTransaction(long millis) throws InterruptedException {
        repo.countRows("polls");                       // 最初のSQLで、コネクションを取得してトランザクションが始まる
        Thread.sleep(millis);                          // 外部APIの呼び出しの代わり（待っているだけ）
        repo.countRows("polls");
        return "external (in tx) done";
    }

    /** 直した例: 外部通信はトランザクションの外。DBを使う部分だけを短いトランザクションにする。 */
    public String externalCallOutsideTransaction(long millis) throws InterruptedException {
        Thread.sleep(millis);                          // 先に済ませる。この間、コネクションは要らない
        return transaction.execute(status -> {         // DBを使う部分だけを、短いトランザクションにする
            repo.countRows("polls");
            repo.countRows("polls");
            return "external (outside tx) done";
        });
    }

    /**
     * REQUIRES_NEW は「もう1本」コネクションを要求する。
     * プールが1本だと、外側が握っている1本を待ち続け、自分で自分を詰まらせる。
     */
    @Transactional
    public String needsSecondConnection(long holdMillis) throws InterruptedException {
        repo.countRows("polls");                       // 外側がコネクションを1本握る
        Thread.sleep(holdMillis);                      // 他のリクエストも外側で1本ずつ握る時間を作る
        return audit.recordRequiresNew("2本目の接続が要る");   // 内側が2本目を要求する
    }
}
