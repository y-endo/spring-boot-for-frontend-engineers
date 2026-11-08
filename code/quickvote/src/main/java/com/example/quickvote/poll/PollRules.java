package com.example.quickvote.poll;

import java.time.Duration;

/** QuickVote の仕様上の上限・期間。設定ではなくコードに置く（運用で気軽に変えてよい値ではないため）。 */
public final class PollRules {

    /** 参加者の上限（主催者を含む）。 */
    public static final int MAX_PARTICIPANTS = 50;

    /** 選択肢の上限。 */
    public static final int MAX_OPTIONS = 10;

    /**
     * タイトル・選択肢ラベルの最大文字数。この仕様では「文字数 = Unicode のコードポイント数」とする。
     * 結合文字（が + 濁点 など）や ZWJ で連結した絵文字（家族の絵文字など）は、見た目が1文字でも
     * 複数のコードポイントなので、複数の文字として数える。見た目の文字数（グラフェム数）ではない。
     * UTF-16 の char 数（String.length() や @Size）とも違う。サロゲートペアの絵文字は、1コードポイント。
     */
    public static final int MAX_TEXT_CHARS = 100;

    /** 選択肢ラベルの最大バイト数（UTF-8）。 */
    public static final int MAX_LABEL_BYTES = 400;

    /**
     * 締切は「作成から何分後か」で指定する（5分〜7日）。サーバーが Clock の現在時刻から closes_at を計算する。
     * 絶対時刻で受け取ると、ブラウザの時計のずれや通信時間のぶんだけ「5分後」が5分を割って拒否されてしまう。
     */
    public static final int MIN_CLOSES_IN_MINUTES = 5;
    public static final int MAX_CLOSES_IN_MINUTES = 7 * 24 * 60;

    /** 作成から7日後に物理削除する（締切とは別）。 */
    public static final Duration RETENTION = Duration.ofDays(7);

    private PollRules() {
    }
}
