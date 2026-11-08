package com.example.jpalab.poll;

/** 投票ごとの選択肢の数。集計まで DB にやらせる射影。 */
public record PollSummary(Long id, String title, long optionCount) {
}
