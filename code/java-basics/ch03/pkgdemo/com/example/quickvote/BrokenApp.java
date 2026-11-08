package com.example.quickvote;

import com.example.quickvote.poll.Poll;

public class BrokenApp {
    void main() {
        Poll poll = new Poll("ランチどこ行く？");
        IO.println(poll.title);           // private
        poll.resetForTest();              // package-private（別パッケージ）
        IO.println(poll.describe());      // protected（子クラスではない）
    }
}
