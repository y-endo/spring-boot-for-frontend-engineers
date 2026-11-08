package com.example.jpalab.poll;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPollIdAndParticipantId(Long pollId, Long participantId);

    /** QuickVote の VoteRepository.upsert と同じ SQL を、ネイティブクエリとして書いたもの。 */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO votes (poll_id, participant_id, option_id, voted_at)
            VALUES (:pollId, :participantId, :optionId, :now)
            ON CONFLICT (poll_id, participant_id) DO UPDATE
               SET option_id = EXCLUDED.option_id, voted_at = EXCLUDED.voted_at
               WHERE votes.option_id <> EXCLUDED.option_id
            """)
    int upsert(@Param("pollId") Long pollId, @Param("participantId") Long participantId,
            @Param("optionId") Long optionId, @Param("now") OffsetDateTime now);
}
