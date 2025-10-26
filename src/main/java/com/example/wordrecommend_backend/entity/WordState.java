package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "word_state")
@Data
@NoArgsConstructor
public class WordState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 多個 WordState 對應到一個 User
    @JoinColumn(name = "user_id", nullable = false) // 指定外鍵欄位為 user_id
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) // 多個 WordState 對應到一個 Word
    @JoinColumn(name = "word_id", nullable = false) // 指定外鍵欄位為 word_id
    private Word word;

    // 核心值：記憶強度 M_i(t)，範圍 [0, 1]
    @Column(name = "memory_strength", nullable = false)
    private Double memoryStrength;

    // 離散狀態 (S0, S1, S2, S3)
    // 建議使用 String 儲存，或用 Enum
    @Column(name = "current_state", nullable = false)
    private String currentState;

    // 上次複習的時間戳，用於計算 Deltat (時間差)
    @Column(name = "last_review_time", nullable = false)
    private LocalDateTime lastReviewTime;

    // 計算出的出題優先度 P_i (越大越優先)
    @Column(name = "next_review_priority")
    private Double nextReviewPriority;

    @Column(name = "last_read_time")
    private LocalDateTime lastReadTime;

    // 累計閱讀次數
    @Column(name = "read_count", nullable = false)
    private Integer readCount = 0;

    // 閱讀總時間（秒）
    @Column(name = "total_read_duration", nullable = false)
    private Double totalReadDuration = 0.0;

    // 平均閱讀時間（自動計算或方便查詢）
    @Column(name = "avg_read_duration", nullable = false)
    private Double avgReadDuration = 0.0;
}