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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Enumerated(EnumType.STRING) // 告訴 JPA 將 Enum 的「名稱」存為字串
    @Column(name = "interaction_type", nullable = false)
    private InteractionType interactionType; // 將類型從 String 改為 InteractionType

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    // 答題結果：是否答對
    @Column(name = "is_correct")
    private Boolean isCorrect;

    // 答題發生的時間戳
    @Column(name = "review_time", nullable = false)
    private LocalDateTime reviewTime;
}