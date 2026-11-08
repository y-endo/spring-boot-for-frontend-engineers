package com.example.jpalab.poll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OptionRepository extends JpaRepository<Option, Long> {

    /** QuickVote の OptionRepository.findWithVoteCounts と同じ意味（LEFT JOIN なので票0の選択肢も返る）。 */
    @Query("""
            select new com.example.jpalab.poll.OptionCount(o.id, o.label, count(v))
            from Option o left join Vote v on v.option = o
            where o.poll.id = :pollId
            group by o.id, o.label
            order by o.id
            """)
    List<OptionCount> findWithVoteCounts(@Param("pollId") Long pollId);
}
