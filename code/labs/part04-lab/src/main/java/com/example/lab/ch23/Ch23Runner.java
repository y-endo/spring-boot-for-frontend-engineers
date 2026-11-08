package com.example.lab.ch23;

import java.io.IOException;

import com.example.lab.LabRepository;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** --lab.run=ch23 で動く。@Transactional が「効く場合・効かない場合」を順に観察する。 */
@Component
@ConditionalOnProperty(name = "lab.run", havingValue = "ch23")
public class Ch23Runner implements ApplicationRunner {

    private final LabRepository repo;
    private final SelfCallService selfCall;
    private final PrivateService privateService;
    private final CheckedExceptionService checked;
    private final SwallowService swallow;
    private final OuterService outer;
    private final ReadOnlyService readOnly;
    private final ThreadService threads;

    private int codeSeq = 0;

    public Ch23Runner(LabRepository repo, SelfCallService selfCall, PrivateService privateService,
            CheckedExceptionService checked, SwallowService swallow, OuterService outer, ReadOnlyService readOnly,
            ThreadService threads) {
        this.repo = repo;
        this.selfCall = selfCall;
        this.privateService = privateService;
        this.checked = checked;
        this.swallow = swallow;
        this.outer = outer;
        this.readOnly = readOnly;
        this.threads = threads;
    }

    /** 参加コードは UNIQUE で、使える文字は決まっている（0/O/1/I/L を除く31種）。BBBBBBAA, BBBBBBAB, ... と作る。 */
    private String code() {
        int n = codeSeq++;
        return "BBBBBB" + (char) ('A' + n / 8) + (char) ('A' + n % 8);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long host = repo.insertVisitor();

        title("1. 注入されているのは本物か、プロキシか");
        System.out.println("  クラス名       : " + selfCall.getClass().getName());
        System.out.println("  AOPプロキシ?   : " + AopUtils.isAopProxy(selfCall));
        System.out.println("  CGLIBプロキシ? : " + AopUtils.isCglibProxy(selfCall));
        System.out.println("  本体のクラス   : " + AopUtils.getTargetClass(selfCall).getName());

        title("2. 外から inner() を呼ぶ（プロキシを通る）");
        System.out.println("  " + selfCall.inner());

        title("3. 同じクラスの outer() から this.inner() を呼ぶ（自己呼び出し）");
        System.out.println("  " + selfCall.outer());

        title("4. private メソッドに付けた @Transactional");
        System.out.println("  " + privateService.callHidden());

        title("5. checked 例外 (IOException) を投げる");
        try {
            checked.failWithChecked(code());
        } catch (IOException e) {
            System.out.println("  例外: " + e.getMessage());
        }
        report("checked例外の後（polls に1行残るか）");
        try {
            checked.failWithCheckedRollbackFor(code());
        } catch (IOException e) {
            System.out.println("  例外: " + e.getMessage());
        }
        report("rollbackFor=Exception.class の後（増えないか）");

        title("6. 例外の握りつぶし（アプリの失敗）");
        swallow.swallowAppFailure(code(), host);
        report("握りつぶした後（poll だけ残るか）");

        title("6b. 例外の握りつぶし（DBのエラー）");
        try {
            swallow.swallowDbFailure(code());
        } catch (Exception e) {
            System.out.println("  呼び出し元に届いた例外: " + e.getClass().getSimpleName() + ": " + firstLine(e));
        }
        report("DBエラーを握りつぶした後");

        title("7. REQUIRED: 外側が失敗。内側の監査ログも一緒に消える");
        try {
            outer.businessThenFailWithRequired(code());
        } catch (IllegalStateException e) {
            System.out.println("  例外: " + e.getMessage());
        }
        System.out.println("  lab_audit=" + repo.countRows("lab_audit"));

        title("8. REQUIRES_NEW: 外側が失敗しても、監査ログは残る");
        try {
            outer.businessThenFailWithRequiresNew(code());
        } catch (IllegalStateException e) {
            System.out.println("  例外: " + e.getMessage());
        }
        System.out.println("  lab_audit=" + repo.countRows("lab_audit"));
        report("REQUIRES_NEW の後");

        title("9. 内側(REQUIRED)の失敗を外側が catch する");
        try {
            outer.catchInnerRequired(code());
        } catch (Exception e) {
            System.out.println("  呼び出し元に届いた例外: " + e.getClass().getName());
            System.out.println("    " + firstLine(e));
        }
        report("内側REQUIREDが失敗した後");

        title("10. 内側(REQUIRES_NEW)の失敗を外側が catch する");
        outer.catchInnerRequiresNew(code());
        report("内側REQUIRES_NEWが失敗した後（外側の INSERT は残るか）");

        title("11. readOnly = true");
        System.out.println("  " + readOnly.read());
        try {
            readOnly.writeInReadOnly(code());
        } catch (Exception e) {
            System.out.println("  例外: " + e.getClass().getName());
            System.out.println("    原因: " + rootMessage(e));
        }

        title("12. 別のスレッドで動かした処理は、トランザクションに入らない");
        try {
            threads.insertInAnotherThread(code(), code());
        } catch (IllegalStateException e) {
            System.out.println("  例外: " + e.getMessage());
        }
        report("スレッド実験の後");
    }

    private void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }

    private void report(String when) {
        System.out.printf("  [%s] polls=%d participants=%d%n", when, repo.countRows("polls"),
                repo.countRows("participants"));
    }

    private static String rootMessage(Throwable e) {
        return firstLine(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e));
    }

    private static String firstLine(Throwable e) {
        String message = String.valueOf(e.getMessage());
        int cut = message.indexOf('\n');
        return cut < 0 ? message : message.substring(0, cut);
    }
}
