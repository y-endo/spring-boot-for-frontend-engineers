package com.example.quickvote.poll;

public enum PollStatus {
    OPEN,
    /** 主催者が手動で締め切った状態。時間切れによる締切は status ではなく closes_at で判定する。 */
    CLOSED
}
