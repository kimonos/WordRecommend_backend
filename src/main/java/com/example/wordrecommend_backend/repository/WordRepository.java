package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import org.springframework.data.domain.Pageable; // 【核心修正】請確保您匯入的是這個 Spring Data 的 Pageable！
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

//    @Query("SELECT w FROM Word w WHERE w.id NOT IN (SELECT ws.word.id FROM WordState ws WHERE ws.user = :user) ORDER BY w.complexityScore ASC")
//    List<Word> findNewWords(@Param("user") User user, Pageable pageable); // 這裡的 Pageable 現在也是正確的類型了
    @Query("""
        SELECT w FROM Word w
        LEFT JOIN WordState ws ON ws.word.id = w.id AND ws.user = :user
        WHERE ws.id IS NULL
        ORDER BY function('RANDOM')
    """)
    List<Word> findNewWordsRandomly(@Param("user") User user, Pageable pageable);

    @Query("SELECT w FROM Word w WHERE w.cefrLevel = :level AND w.id NOT IN " +
            "(SELECT ws.word.id FROM WordState ws WHERE ws.user = :user) ORDER BY RANDOM()")
    List<Word> findNewWordsByLevel(@Param("user") User user,
                                   @Param("level") String level,
                                   Pageable pageable);


//        @Query("""
//        SELECT COUNT(w) FROM Word w
//        WHERE w.id NOT IN (
//            SELECT ws.word.id FROM WordState ws WHERE ws.user = :user
//        )
//    """)
//        long countUnlearnedWords(@Param("user") User user);
}