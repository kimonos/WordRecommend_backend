package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.ReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHistoryRepository extends JpaRepository<ReviewHistory, Long> {
    // 這個 Repository 主要用於保存歷史記錄 (save())，暫時不需要自定義查詢
}