package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_history")
@Data
@NoArgsConstructor
public class ReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 外鍵：連結到 Word 表格
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    // 答題結果：是否答對
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    // 答題時間 (毫秒)，用於計算回饋函數 f 的速度權重
    @Column(name = "answer_time_ms", nullable = false)
    private Long answerTimeMs;

    // 答題發生的時間戳
    @Column(name = "review_time", nullable = false)
    private LocalDateTime reviewTime;
}