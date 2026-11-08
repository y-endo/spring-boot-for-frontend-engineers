package com.example.lab.ch26;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 楽観ロックの実験。メモ（lab_memos）を「読んでから、書き戻す」2人の編集者。 */
@Service
public class MemoService {

    public record Memo(long id, String body, long version) {
    }

    private final JdbcClient jdbc;

    public MemoService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void create(long id, String body) {
        jdbc.sql("INSERT INTO lab_memos (id, body) VALUES (:id, :body)").param("id", id).param("body", body).update();
    }

    public Memo find(long id) {
        return jdbc.sql("SELECT id, body, version FROM lab_memos WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Memo(rs.getLong("id"), rs.getString("body"), rs.getLong("version")))
                .single();
    }

    /** 悪い例: 読んだ時点から変わっていないかを確かめずに上書きする（後勝ち）。 */
    @Transactional
    public void saveLastWriteWins(long id, String newBody) {
        jdbc.sql("UPDATE lab_memos SET body = :body WHERE id = :id").param("id", id).param("body", newBody).update();
    }

    /**
     * 楽観ロック: 「読んだときの version のままなら更新する」を、WHERE に書いた1本の UPDATE にする。
     * 更新件数が0なら、読んだ後に誰かが更新した。
     */
    @Transactional
    public void saveOptimistic(long id, String newBody, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE lab_memos SET body = :body, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id)
                .param("body", newBody)
                .param("version", expectedVersion)
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("メモ " + id + " は、読んだ後に他の人が更新しました");
        }
    }
}
