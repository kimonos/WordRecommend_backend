package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WordStateRepository extends JpaRepository<WordState, Long> {

    // 撰寫一個自定義方法：根據單字查詢其當前狀態
    // 這是我們在 StateUpdateService 中更新狀態的關鍵
    Optional<WordState> findByWord(Word word);
}