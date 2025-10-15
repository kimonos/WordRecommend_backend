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

    // 外鍵：連結到 Word 表格
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "word_id", nullable = false)
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

    /**
     * 注意：在未來納入使用者後，這個表格會增加 'user_id' 外鍵。
     * 為了現在的單純演算法，我們可以假設這是「全域的單字狀態」。
     */
}