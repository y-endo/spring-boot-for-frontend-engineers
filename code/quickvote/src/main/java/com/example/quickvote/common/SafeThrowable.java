package com.example.quickvote.common;

import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 例外をログに出すとき、メッセージを落として「型・SQLSTATE・スタックのフレーム」だけにした例外へ作り替える。
 * <p>
 * {@code log.error("...", ex)} は、ex のメッセージだけでなく、原因（cause）のメッセージもすべて出力する。
 * 「自分のメッセージに入力値を連結しなければ安全」とは言えない。原因側のメッセージは自分では制御できないからだ。
 * 例えば PostgreSQL の CHECK 制約違反の例外は、メッセージに違反した行の中身
 * （{@code Failing row contains (...)} に、タイトルや参加コードなど）を含む。
 * <p>
 * そこで、ログに残す情報を許可リスト方式で決める:
 * 例外のクラス名、SQLSTATE（制約違反の種類を表す5文字のコード。値は含まない）、スタックのフレーム。
 * 例外のメッセージは、どれも出さない。
 */
public final class SafeThrowable {

    private static final int MAX_DEPTH = 10;

    private SafeThrowable() {
    }

    /** original を、メッセージを含まない例外（原因の連鎖も同様）に作り替える。original 自体は変更しない。 */
    public static Throwable of(Throwable original) {
        return copy(original, 0, new IdentityHashMap<>());
    }

    private static Throwable copy(Throwable original, int depth, Map<Throwable, Boolean> seen) {
        StringBuilder description = new StringBuilder(original.getClass().getName());
        if (original instanceof SQLException sql && sql.getSQLState() != null) {
            description.append(" [SQLSTATE=").append(sql.getSQLState()).append(']');
        }
        Redacted copy = new Redacted(description.toString());
        copy.setStackTrace(original.getStackTrace());
        seen.put(original, Boolean.TRUE);
        Throwable cause = original.getCause();
        // 原因が循環していても、深すぎても止まるようにする。
        if (cause != null && depth < MAX_DEPTH && !seen.containsKey(cause)) {
            copy.initCause(copy(cause, depth + 1, seen));
        }
        return copy;
    }

    /** ログ上の型名。メッセージ欄には元の例外のクラス名（と SQLSTATE）だけが入る。 */
    private static final class Redacted extends Exception {

        private Redacted(String description) {
            super(description);
        }
    }
}
