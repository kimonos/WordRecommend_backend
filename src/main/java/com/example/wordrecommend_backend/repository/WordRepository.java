package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

    // 未學過的新字，隨機取
    @Query("""
        SELECT w FROM Word w
        LEFT JOIN WordState ws ON ws.word = w AND ws.user = :user
        WHERE ws.id IS NULL
        ORDER BY function('random')
    """)
    List<Word> findNewWordsRandomly(@Param("user") User user, Pageable pageable);

    // 未學過 + 指定 CEFR 等級，隨機取
    @Query("""
        SELECT w FROM Word w
        WHERE w.cefrLevel = :level
          AND w.id NOT IN (
              SELECT ws.word.id FROM WordState ws WHERE ws.user = :user
          )
        ORDER BY function('random')
    """)
    List<Word> findNewWordsByLevel(@Param("user") User user,
                                   @Param("level") String level,
                                   Pageable pageable);

    // 未學過的總數
    @Query("""
        SELECT COUNT(w) FROM Word w
        WHERE w.id NOT IN (
            SELECT ws.word.id FROM WordState ws WHERE ws.user = :user
        )
    """)
    long countNewWords(@Param("user") User user);

    // 產生干擾選項（同詞性、相近難度、排除本題）
    @Query("""
        SELECT w FROM Word w
        WHERE w.partOfSpeech = :pos
          AND w.complexityScore BETWEEN :minComplexity AND :maxComplexity
          AND w.id <> :excludeId
        ORDER BY function('random')
    """)
    List<Word> findCandidateDistractors(
            @Param("pos") String partOfSpeech,
            @Param("minComplexity") Double complexityMin,
            @Param("maxComplexity") Double complexityMax,
            @Param("excludeId") Long excludeWordId,
            Pageable pageable
    );

    // 降級策略：只要同詞性（不足時擴大範圍）
    @Query("""
        SELECT w FROM Word w
        WHERE w.partOfSpeech = :pos
          AND w.id <> :excludeId
        ORDER BY function('random')
    """)
    List<Word> findWordsByPartOfSpeech(
            @Param("pos") String partOfSpeech,
            @Param("excludeId") Long excludeWordId,
            Pageable pageable
    );
}
