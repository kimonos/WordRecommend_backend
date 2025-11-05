package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用訊息響應 DTO
 *
 * 用途：
 * - 返回簡單的成功/失敗訊息
 * - 適用於各種 API 端點
 *
 * 響應範例：
 * {
 *   "message": "密碼重置連結已發送到您的郵箱"
 * }
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    /**
     * 訊息內容
     *
     * 範例：
     * - "密碼重置連結已發送到您的郵箱"
     * - "密碼重置成功"
     * - "Token 無效或已過期"
     */
    private String message;
}