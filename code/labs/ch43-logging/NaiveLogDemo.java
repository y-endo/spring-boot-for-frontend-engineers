import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.example.quickvote.common.SafeThrowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「例外をそのままログに渡す」と何が出るかを確かめる実験。
 * 実際の CHECK 制約（polls_closes_at_check）に違反する UPDATE を実行し、その例外を2通りでログに出す。
 */
public class NaiveLogDemo {

    private static final Logger log = LoggerFactory.getLogger("demo");

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:25442/quickvote";
        try (Connection c = DriverManager.getConnection(url, "quickvote", "quickvote")) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE polls SET closes_at = created_at - interval '1 hour' WHERE id = (SELECT min(id) FROM polls)");
            } catch (SQLException e) {
                // 悪い例：メッセージを自分で連結していなくても、原因（cause）のメッセージごと出力される
                log.error("想定外のエラーが発生しました", e);
                // 良い例：メッセージを落として、型・SQLSTATE・スタックだけにしてから渡す
                log.error("想定外のエラーが発生しました", SafeThrowable.of(e));
            } finally {
                c.rollback();
            }
        }
    }
}
