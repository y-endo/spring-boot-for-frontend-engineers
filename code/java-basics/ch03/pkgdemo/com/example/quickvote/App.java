package com.example.quickvote;

import com.example.quickvote.poll.Poll;

public class App {
    void main() {
        Poll poll = new Poll("ランチどこ行く？");
        poll.vote();
        poll.vote();
        IO.println(poll.title() + " : " + poll.voteCount() + "票");
    }
}
