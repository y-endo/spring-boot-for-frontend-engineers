package com.example.review1.infra;

/** テストが「数えた直後」に割り込むための窓口（Mockito のスパイを掛ける場所）。 */
public interface MemberStore {

    int countParticipants(long pollId);
}
