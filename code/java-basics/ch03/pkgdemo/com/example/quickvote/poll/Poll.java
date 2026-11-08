package com.example.quickvote.poll;

public class Poll {
    private final String title;          // このクラスの中からだけ見える
    private int voteCount;

    public Poll(String title) {          // どこからでも new できる
        this.title = title;
    }

    public String title() {              // 外部に公開する入口
        return title;
    }

    public int voteCount() {
        return voteCount;
    }

    public void vote() {
        voteCount++;
    }

    void resetForTest() {                // 修飾子なし＝同じパッケージからだけ見える
        voteCount = 0;
    }

    protected String describe() {        // 同じパッケージ、または子クラスから見える
        return title + "（" + voteCount + "票）";
    }
}
