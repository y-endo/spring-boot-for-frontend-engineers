import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「各層で ログに出して、投げ直す」と、1回の失敗がログに何回出るかを数える実験。
 * 層は Repository → Service → Controller。悪い例は3層すべてで log.error してから投げ直す。
 */
public class LogOnceDemo {

    private static final Logger repoLog = LoggerFactory.getLogger("demo.Repository");
    private static final Logger serviceLog = LoggerFactory.getLogger("demo.Service");
    private static final Logger controllerLog = LoggerFactory.getLogger("demo.Controller");

    // ---- 悪い例：各層で出して、投げ直す ----
    static void badRepository() {
        try {
            throw new IllegalStateException("DBに届きません");
        } catch (RuntimeException e) {
            repoLog.error("保存に失敗しました", e);
            throw e;
        }
    }

    static void badService() {
        try {
            badRepository();
        } catch (RuntimeException e) {
            serviceLog.error("投票の処理に失敗しました", e);
            throw e;
        }
    }

    static void badController() {
        try {
            badService();
        } catch (RuntimeException e) {
            controllerLog.error("APIが失敗しました", e);
            throw e;
        }
    }

    // ---- 良い例：下の層は投げるだけ。出すのは、受け止める1か所（ProblemDetailAdvice に当たる場所）だけ ----
    static void goodRepository() {
        throw new IllegalStateException("DBに届きません");
    }

    static void goodService() {
        goodRepository();
    }

    public static void main(String[] args) {
        System.out.println("=== 悪い例 ===");
        try {
            badController();
        } catch (RuntimeException e) {
            // 最後に、フレームワークの例外ハンドラがもう一度出す
            controllerLog.error("想定外のエラーが発生しました", e);
        }
        System.out.println("=== 良い例 ===");
        try {
            goodService();
        } catch (RuntimeException e) {
            controllerLog.error("想定外のエラーが発生しました", e);
        }
    }
}
