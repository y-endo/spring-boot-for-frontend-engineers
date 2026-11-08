package com.example.quickvote.poll;

import java.time.Duration;

/** QuickVote の仕様上の上限・期間。設定ではなくコードに置く（運用で気軽に変えてよい値ではないため）。 */
public final class PollRules {

    /** 参加者の上限（主催者を含む）。 */
    public static final int MAX_PARTICIPANTS = 50;

    /** 選択肢の上限。 */
    public static final int MAX_OPTIONS = 10;

    /** タイトル・選択肢ラベルの最大文字数（コードポイント数）。 */
    public static final int MAX_TEXT_CHARS = 100;

    /** 選択肢ラベルの最大バイト数（UTF-8）。 */
    public static final int MAX_LABEL_BYTES = 400;

    /** 締切は作成時点から5分後〜7日後。クライアントは「何分後か」（closesInMinutes）を送り、締切の時刻はサーバーが決める。 */
    public static final int MIN_CLOSES_IN_MINUTES = 5;
    public static final int MAX_CLOSES_IN_MINUTES = 7 * 24 * 60;

    /** 作成から7日後に物理削除する（締切とは別）。 */
    public static final Duration RETENTION = Duration.ofDays(7);

    private PollRules() {
    }
}
