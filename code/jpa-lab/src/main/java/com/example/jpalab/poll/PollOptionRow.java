package com.example.jpalab.poll;

/** 投票×選択肢をJOINした「平らな1行」。DTO射影で取るときの受け皿。 */
public record PollOptionRow(Long pollId, String title, String label) {
}
