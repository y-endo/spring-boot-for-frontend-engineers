package com.example.jpalab.poll;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollRepository extends JpaRepository<Poll, Long> {

    // 1. メソッド名から JPQL が作られる（派生クエリ）。実装は書かない。
    long countByTitle(String title);

    List<Poll> findAllByOrderByIdAsc();

    // 2. fetch join: 親と子を1本のSQLで取る。
    @Query("select p from Poll p join fetch p.options order by p.id")
    List<Poll> findAllWithOptions();

    // 2b. fetch join とページング。Hibernate 7.4 は、親の絞り込みをサブクエリの中でやる SQL を作る（第40章）。
    //     JPQL の文字列を findAllWithOptions と変えている理由は、第40章の「壊してみる」を参照。
    @Query(value = "select p from Poll p join fetch p.options o order by p.id", countQuery = "select count(p) from Poll p")
    Page<Poll> findPageWithOptions(Pageable pageable);

    // 2c. 定石: 先に親のIDだけをページングで取り、そのIDで fetch join する（2本）
    @Query("select p.id from Poll p order by p.id")
    List<Long> findIds(Pageable pageable);

    @Query("select p from Poll p join fetch p.options where p.id in :ids order by p.id")
    List<Poll> findWithOptionsByIdIn(@Param("ids") List<Long> ids);

    // 3. @EntityGraph: 「options も一緒に取る」を宣言する。JPQL を書かなくてよい。
    @EntityGraph(attributePaths = "options")
    List<Poll> findAllWithGraphByOrderByIdAsc();

    // 4. DTO 射影: Entity にせず、必要な列だけを DTO で受け取る。
    @Query("""
            select new com.example.jpalab.poll.PollOptionRow(p.id, p.title, o.label)
            from Poll p join p.options o
            order by p.id, o.id
            """)
    List<PollOptionRow> findOptionRows();

    @Query("""
            select new com.example.jpalab.poll.PollSummary(p.id, p.title, count(o))
            from Poll p left join p.options o
            group by p.id, p.title
            order by p.id
            """)
    List<PollSummary> findSummaries();

    // 5. 悲観ロック（QuickVote の PollRepository.lockById に相当）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poll p where p.id = :id")
    Optional<Poll> lockById(@Param("id") Long id);

    // 6. 更新系の JPQL（バルク更新）。永続コンテキストを経由せず、DB へ直接 UPDATE を発行する。
    @Modifying
    @Query("update Poll p set p.status = com.example.jpalab.poll.PollStatus.CLOSED where p.id = :id")
    int closeInBulk(@Param("id") Long id);
}
