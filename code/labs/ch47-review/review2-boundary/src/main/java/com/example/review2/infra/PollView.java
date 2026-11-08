package com.example.review2.infra;

/** 応答用の形。 */
public record PollView(long id, String title) {

    public static PollView of(PollData poll) {
        return new PollView(poll.id(), poll.title());
    }
}
