package com.example.mybatislab;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Java 側は「メソッド名と引数・戻り値だけ」のインターフェース。SQL は src/main/resources/mapper/PollMapper.xml にある。
 * XML の namespace がこのインターフェースの完全修飾名、XML の id がメソッド名、という対応で結び付く。
 */
@Mapper
public interface PollMapper {

    List<OptionCount> findWithVoteCounts(@Param("pollId") long pollId);

    int upsertVote(@Param("pollId") long pollId, @Param("participantId") long participantId,
            @Param("optionId") long optionId);

    /** 入れ子の select（collection の select=）で子を取る。1 + N 本になる。 */
    List<PollWithOptions> listWithOptionsNested();

    /** JOIN の結果を collection に詰め直す。1 本。 */
    List<PollWithOptions> listWithOptionsJoin();

    List<PollRow> search(@Param("status") String status, @Param("keyword") String keyword,
            @Param("orderBy") String orderBy);
}
