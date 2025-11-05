package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 驗證響應 DTO
 *
 * 用途：
 * - 前端在顯示重置密碼頁面前，先驗證 Token 是否有效
 *
 * 端點：GET /auth/reset-password/validate?token=xxx
 *
 * 響應範例：
 * {
 *   "valid": true
 * }
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResponse {

    /**
     * Token 是否有效
     *
     * 有效條件：
     * 1. Token 存在
     * 2. Token 未過期
     * 3. Token 未被使用
     *
     * 範例：
     * - true：Token 有效，可以重置密碼
     * - false：Token 無效，顯示錯誤訊息
     */
    private boolean valid;
}