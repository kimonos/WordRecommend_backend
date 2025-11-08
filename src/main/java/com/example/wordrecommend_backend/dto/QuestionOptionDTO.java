package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 題目選項 DTO
 *
 * 用途：
 * - 前端顯示選項
 * - 記錄是否為正確答案（後端用，不傳給前端）
 *
 * @since Phase 7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDTO {

    /**
     * 選項 ID（唯一識別符）
     * 用途：前端選擇時返回此 ID，後端據此判斷
     */
    private Long id;

    /**
     * 選項內容（展示給使用者）
     *
     * 根據題型不同：
     * - 簡單題：中文翻譯
     * - 普通題：英文單字
     * - 困難題：N/A（無選項）
     */
    private String content;

    /**
     * 是否為正確答案（後端內部標記，不傳給前端）
     *
     * 🔑 安全考慮：
     * - 序列化時忽略此欄位（前端不應知道正確答案）
     * - 只在後端驗證時使用
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Boolean isCorrect;

    // ==================== 輔助構造方法 ====================

    /**
     * 創建選項（不暴露正確答案）
     */
    public static QuestionOptionDTO createOption(Long wordId, String content) {
        return new QuestionOptionDTO(wordId, content, null);
    }

    /**
     * 創建選項（內部用，包含正確答案標記）
     */
    public static QuestionOptionDTO createOptionInternal(Long wordId, String content, Boolean isCorrect) {
        return new QuestionOptionDTO(wordId, content, isCorrect);
    }
}