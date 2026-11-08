package com.example.review3.fixed;

/** API に出す形。DB の行（内部の列）とは別に、出してよいものだけを持つ。 */
public record PollView(long id, String title) {
}
