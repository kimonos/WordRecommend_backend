package com.example.wordrecommend_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 使用者答題提交 DTO
 *
 * 用途：
 * - 前端提交答案時使用
 * - 包含題目 ID、選擇的答案、答題時間等
 *
 * @since Phase 7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerSubmissionDTO {

    /**
     * 題目 ID
     *
     * 用途：後端識別是哪道題
     */
    @NotNull(message = "Question ID cannot be null")
    private Long questionId;

    /**
     * 單字 ID
     *
     * 用途：識別作答的單字
     */
    @NotNull(message = "Word ID cannot be null")
    private Long wordId;

    /**
     * 使用者的答案
     *
     * 根據題型不同的含義：
     * - EASY/NORMAL：被選中的選項 ID（Long）
     * - HARD：拼寫的英文單字（String）
     *
     * 🔑 實作：可以統一使用 String，後端根據題型進行轉換
     */
    @NotBlank(message = "Answer cannot be blank")
    private String selectedAnswer;

    /**
     * 答題耗時（毫秒）
     *
     * 用途：
     * - 計算速度獎勵因子
     * - 分析學習行為
     */
    @NotNull(message = "Answer time cannot be null")
    private Long answerTimeMs;

    /**
     * 題目類型（前端回傳，用於後端驗證）
     *
     * 可能的值：EASY, NORMAL, HARD
     */
    @NotBlank(message = "Question type cannot be blank")
    private String questionType;
}