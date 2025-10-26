package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import org.springframework.data.domain.Pageable; // 【核心修正】請確保您匯入的是這個 Spring Data 的 Pageable！
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WordStateRepository extends JpaRepository<WordState, Long> {

    Optional<WordState> findByUserAndWord(User user, Word word);
//
//    @Query("SELECT ws FROM WordState ws WHERE ws.user = :user AND ws.currentState IN ('S1', 'S2', 'S3') ORDER BY ws.nextReviewPriority DESC")
//    List<WordState> findReviewWords(@Param("user") User user, Pageable pageable); // 這裡的 Pageable 現在是正確的類型了
    @Query("SELECT ws FROM WordState ws WHERE ws.user = :user AND ws.currentState = :state ORDER BY function('RANDOM')")
    List<WordState> findByUserAndState(@Param("user") User user, @Param("state") String state, Pageable pageable);

    @Query("SELECT COUNT(ws) FROM WordState ws WHERE ws.user = :user AND ws.currentState = :state")
    long countByUserAndState(@Param("user") User user, @Param("state") String state);

}