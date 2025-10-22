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
}