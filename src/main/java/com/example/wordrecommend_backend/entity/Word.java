package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "words")
@Data
@NoArgsConstructor
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 單字本身 (Word text)
    @Column(name = "word_text", nullable = false, unique = true)
    private String wordText;

    // 中文翻譯
    @Column(name = "translation", nullable = false)
    private String translation;

    @Column(name = "part_of_speech", nullable = false)
    private String partOfSpeech;

    @Column(name = "cefr_level", nullable = false)
    private String cefrLevel;

    // 單字複雜度分數 (Complexity Score, C_i)
    // 預設為 1.0，未來可用來調整初始難度
    @Column(name = "complexity_score")
    private Double complexityScore = 1.0;

    // 可以在此加入其他語言學特徵欄位，例如 lexeme_tags
    // ...
}