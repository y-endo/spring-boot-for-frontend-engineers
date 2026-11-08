package com.example.jpalab.poll;

/** 選択肢と得票数。QuickVote の OptionRepository.OptionCount と同じ形。 */
public record OptionCount(long id, String label, long votes) {
}
