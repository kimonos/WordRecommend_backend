package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 複習會話 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionDTO {

    /**
     * 會話 ID（用於追蹤）
     */
    private String sessionId;

    /**
     * 總題數
     */
    private Integer totalQuestions;

    /**
     * 本會話中將要使用的單字 ID 列表
     * （10 個不同的單字 ID）
     */
    private List<Long> wordIds;
}