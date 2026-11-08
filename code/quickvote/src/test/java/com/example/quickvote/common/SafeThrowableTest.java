package com.example.quickvote.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/** ログに渡す例外から、メッセージ（入力値を含みうる）が消えていること。 */
class SafeThrowableTest {

    private static String printed(Throwable throwable) {
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    /** PostgreSQL の CHECK 制約違反を模す。メッセージに、違反した行の中身（タイトル・参加コード）が入る。 */
    private static Exception databaseFailure() {
        SQLException cause = new SQLException(
                "ERROR: new row for relation \"polls\" violates check constraint \"polls_closes_after_created\"\n"
                        + "  Detail: Failing row contains (1, 秘密のタイトル-7f3a9c, ABCD2345, OPEN)",
                "23514");
        return new DataIntegrityViolationException("PreparedStatementCallback; 入力値 秘密のラベル-1b2c3d", cause);
    }

    @Test
    void そのままログに出すと原因の例外のメッセージまで出る_これが漏れの原因() {
        // 前提の確認: 例外オブジェクトをそのまま出力すると、cause のメッセージも出る。
        assertThat(printed(databaseFailure())).contains("秘密のタイトル-7f3a9c", "ABCD2345", "秘密のラベル-1b2c3d");
    }

    @Test
    void 作り替えた例外には_メッセージも原因のメッセージも含まれない() {
        Throwable safe = SafeThrowable.of(databaseFailure());

        String output = printed(safe);

        assertThat(output).doesNotContain("秘密のタイトル-7f3a9c", "ABCD2345", "秘密のラベル-1b2c3d", "Failing row");
        // 残るのは、例外の型・SQLSTATE（制約違反の種類）・スタックのフレーム
        assertThat(output).contains("org.springframework.dao.DataIntegrityViolationException")
                .contains("java.sql.SQLException [SQLSTATE=23514]")
                .contains("SafeThrowableTest.databaseFailure");
        assertThat(safe.getCause()).isNotNull();
    }

    @Test
    void 元の例外は変更されない() {
        Exception original = databaseFailure();

        SafeThrowable.of(original);

        assertThat(original.getMessage()).contains("秘密のラベル-1b2c3d");
        assertThat(original.getCause().getMessage()).contains("秘密のタイトル-7f3a9c");
    }

    @Test
    void 原因が循環していても止まる() {
        Exception a = new Exception("a 秘密");
        Exception b = new Exception("b 秘密", a);
        a.initCause(b);

        Throwable safe = SafeThrowable.of(a);

        assertThat(printed(safe)).doesNotContain("秘密");
    }
}
