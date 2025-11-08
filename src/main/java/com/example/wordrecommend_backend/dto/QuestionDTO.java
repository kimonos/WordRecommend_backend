package com.example.wordrecommend_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 完整題目 DTO
 *
 * 用途：
 * - API 返回給前端的題目結構
 * - 包含題目、選項、題型、難度等資訊
 *
 * @since Phase 7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDTO {

    // ==================== 題目基本資訊 ====================

    /**
     * 題目 ID（用於之後追蹤）
     */
    private Long questionId;

    /**
     * 主題單字 ID
     */
    private Long wordId;

    /**
     * 題型
     *
     * 可能的值：
     * - "EASY"（簡單）：英文 → 中文選擇
     * - "NORMAL"（普通）：中文 → 英文選擇
     * - "HARD"（困難）：中文 → 英文拼寫
     */
    private String questionType;

    /**
     * 難度級別（用於顯示或分析）
     *
     * 範圍：1-4
     * - 1：最簡單
     * - 4：最困難
     */
    private Integer difficultyLevel;

    // ==================== 題目內容 ====================

    /**
     * 題目題幹
     *
     * 根據題型不同：
     * - EASY：英文單字（例如："apple"）
     * - NORMAL：中文翻譯（例如："蘋果"）
     * - HARD：中文翻譯（例如："蘋果"）
     */
    private String questionText;

    /**
     * 題目類型的提示文字
     *
     * 例如：
     * - EASY："Choose the correct Chinese translation"
     * - NORMAL："Choose the correct English word"
     * - HARD："Type the correct English spelling"
     */
    private String questionInstruction;

    // ==================== 選項 ====================

    /**
     * 選項列表
     *
     * - EASY/NORMAL：包含 4 個選項（已打亂順序）
     * - HARD：空列表或 null（無選項）
     *
     * 🔑 安全考慮：
     * - isCorrect 欄位不會序列化傳給前端
     * - 只在後端驗證時使用
     */
    private List<QuestionOptionDTO> options;

    // ==================== 單字資訊 ====================

    /**
     * 使用者對該單字的目前記憶強度
     *
     * 範圍：0.0 - 1.0
     * - 用於前端顯示進度條或其他可視化
     */
    private Double currentMemoryStrength;

    /**
     * 使用者對該單字的目前狀態
     *
     * 可能的值：S0, S1, S2, S3, S-1
     */
    private String currentState;

    // ==================== 後端內部欄位（不傳給前端）====================

    /**
     * 正確答案的選項 ID
     *
     * 🔑 安全考慮：
     * - 此欄位不會序列化傳給前端
     * - 只在後端驗證答題時使用
     */
    @JsonIgnore
    private Long correctAnswerId;

    /**
     * 生成時的時間戳記（用於追蹤）
     */
    @JsonIgnore
    private Long generatedTimestamp;

    // ==================== 靜態工廠方法 ====================

    /**
     * 建立簡單題（英 → 中）
     */
    public static QuestionDTO createEasyQuestion(
            Long questionId,
            Long wordId,
            String wordText,
            Double memoryStrength,
            String currentState,
            List<QuestionOptionDTO> options,
            Long correctAnswerId) {

        return QuestionDTO.builder()
                .questionId(questionId)
                .wordId(wordId)
                .questionType("EASY")
                .difficultyLevel(1)
                .questionText(wordText)
                .questionInstruction("Choose the correct Chinese translation")
                .options(options)
                .currentMemoryStrength(memoryStrength)
                .currentState(currentState)
                .correctAnswerId(correctAnswerId)
                .generatedTimestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 建立普通題（中 → 英）
     */
    public static QuestionDTO createNormalQuestion(
            Long questionId,
            Long wordId,
            String translation,
            Double memoryStrength,
            String currentState,
            List<QuestionOptionDTO> options,
            Long correctAnswerId) {

        return QuestionDTO.builder()
                .questionId(questionId)
                .wordId(wordId)
                .questionType("NORMAL")
                .difficultyLevel(3)
                .questionText(translation)
                .questionInstruction("Choose the correct English word")
                .options(options)
                .currentMemoryStrength(memoryStrength)
                .currentState(currentState)
                .correctAnswerId(correctAnswerId)
                .generatedTimestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 建立困難題（中 → 英拼寫）
     */
    public static QuestionDTO createHardQuestion(
            Long questionId,
            Long wordId,
            String translation,
            Double memoryStrength,
            String currentState) {

        return QuestionDTO.builder()
                .questionId(questionId)
                .wordId(wordId)
                .questionType("HARD")
                .difficultyLevel(4)
                .questionText(translation)
                .questionInstruction("Type the correct English spelling")
                .options(null)  // 無選項
                .currentMemoryStrength(memoryStrength)
                .currentState(currentState)
                .correctAnswerId(null)  // 拼寫題不需要選項 ID
                .generatedTimestamp(System.currentTimeMillis())
                .build();
    }
}