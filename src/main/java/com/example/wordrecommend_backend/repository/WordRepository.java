package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import org.springframework.data.domain.Pageable; // 【核心修正】請確保您匯入的是這個 Spring Data 的 Pageable！
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

    @Query("SELECT w FROM Word w WHERE w.id NOT IN (SELECT ws.word.id FROM WordState ws WHERE ws.user = :user) ORDER BY w.complexityScore ASC")
    List<Word> findNewWords(@Param("user") User user, Pageable pageable); // 這裡的 Pageable 現在也是正確的類型了
}