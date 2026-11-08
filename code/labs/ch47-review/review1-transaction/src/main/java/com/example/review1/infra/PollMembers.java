package com.example.review1.infra;

import java.io.IOException;

/** 演習1の対象。bad と fixed の両方がこの契約を実装する（テストは同じものを両方に流す）。 */
public interface PollMembers {

    /** 参加する。参加者は50人まで。 */
    void join(long pollId, long visitorId);

    /** 選択肢を追加し、「選択肢が追加された」イベントを履歴に残す。 */
    void addOption(long pollId, String label);

    /** 締め切る。締切時点のスナップショットをアーカイブに書き出す。 */
    void close(long pollId) throws IOException;
}
