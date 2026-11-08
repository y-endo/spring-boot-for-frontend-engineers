package com.example.review1.infra;

import java.io.IOException;

/** 締切時点のスナップショットを、外部のストレージ（ファイル・オブジェクトストレージ）へ書き出す。 */
public interface ArchiveWriter {

    void writeSnapshot(long pollId) throws IOException;
}
