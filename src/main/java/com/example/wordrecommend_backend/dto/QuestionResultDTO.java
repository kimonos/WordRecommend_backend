package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答題結果 DTO
 *
 * 用途：
 * - 返回給前端的答題結果
 * - 包含是否正確、更新後的狀態、反饋信息等
 *
 * @since Phase 7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionResultDTO {

    /**
     * 是否答對
     */
    private Boolean isCorrect;

    /**
     * 使用者的答案
     *
     * 用途：回顯給使用者（讓他們看到自己選了什麼）
     */
    private String userAnswer;

    /**
     * 正確答案
     *
     * 用途：顯示正確答案（答題後才透露）
     */
    private String correctAnswer;

    /**
     * 更新後的記憶強度
     *
     * 用途：顯示進度條
     */
    private Double newMemoryStrength;

    /**
     * 記憶強度變化
     *
     * 計算：newMemoryStrength - previousMemoryStrength
     * 用途：顯示增加或減少的量
     */
    private Double memoryStrengthDelta;

    /**
     * 更新後的 FSM 狀態
     *
     * 用途：顯示當前學習進度
     */
    private String newState;

    /**
     * 狀態是否變化
     *
     * 用途：若有變化，可以顯示「升級」或「降級」的動畫
     */
    private Boolean stateChanged;

    /**
     * 是否遺忘
     *
     * 用途：若遺忘，可以顯示「🔴 遺忘」的提醒
     */
    private Boolean forgotten;

    /**
     * 反饋信息
     *
     * 範例：
     * - "🎉 正確！記憶強度 +0.15"
     * - "❌ 錯誤。正確答案是：apple"
     * - "🔴 你忘記了這個單字，已重新開始學習"
     */
    private String feedbackMessage;

    /**
     * 下一次推薦優先度
     *
     * 用途：顯示該單字是否需要頻繁複習
     */
    private Double nextReviewPriority;

    // ==================== 靜態工廠方法 ====================

    /**
     * 答對時的結果
     */
    public static QuestionResultDTO createCorrectResult(
            String userAnswer,
            String correctAnswer,
            Double newMemoryStrength,
            Double previousMemoryStrength,
            String newState,
            Boolean stateChanged,
            Double nextReviewPriority) {

        double delta = newMemoryStrength - previousMemoryStrength;
        String feedback = String.format("🎉 正確！記憶強度 +%.2f", delta);

        return QuestionResultDTO.builder()
                .isCorrect(true)
                .userAnswer(userAnswer)
                .correctAnswer(correctAnswer)
                .newMemoryStrength(newMemoryStrength)
                .memoryStrengthDelta(delta)
                .newState(newState)
                .stateChanged(stateChanged)
                .forgotten(false)
                .feedbackMessage(feedback)
                .nextReviewPriority(nextReviewPriority)
                .build();
    }

    /**
     * 答錯時的結果
     */
    public static QuestionResultDTO createIncorrectResult(
            String userAnswer,
            String correctAnswer,
            Double newMemoryStrength,
            Double previousMemoryStrength,
            String newState,
            Boolean stateChanged,
            Boolean forgotten,
            Double nextReviewPriority) {

        double delta = newMemoryStrength - previousMemoryStrength;
        String feedback;

        if (forgotten) {
            feedback = String.format("🔴 你忘記了這個單字！正確答案：%s", correctAnswer);
        } else {
            feedback = String.format("❌ 錯誤。正確答案：%s（記憶強度 %.2f）", correctAnswer, delta);
        }

        return QuestionResultDTO.builder()
                .isCorrect(false)
                .userAnswer(userAnswer)
                .correctAnswer(correctAnswer)
                .newMemoryStrength(newMemoryStrength)
                .memoryStrengthDelta(delta)
                .newState(newState)
                .stateChanged(stateChanged)
                .forgotten(forgotten)
                .feedbackMessage(feedback)
                .nextReviewPriority(nextReviewPriority)
                .build();
    }
}