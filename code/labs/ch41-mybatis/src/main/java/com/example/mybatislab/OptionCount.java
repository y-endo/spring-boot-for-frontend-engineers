package com.example.mybatislab;

/** 選択肢と得票数。QuickVote / jpa-lab と同じ形。 */
public record OptionCount(long id, String label, long votes) {
}
