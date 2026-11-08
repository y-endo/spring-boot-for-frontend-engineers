package com.example.review3.infra;

/** 票をDBに記録する（DBアクセスの代わり。テストが「ここで待たせる」ために差し込む場所）。 */
public interface VoteRecorder {

    void record(long optionId);
}
