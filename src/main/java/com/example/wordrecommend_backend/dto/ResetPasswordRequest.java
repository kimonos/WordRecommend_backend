package com.example.wordrecommend_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密碼請求 DTO
 *
 * 用途：
 * - 使用者提交新密碼
 *
 * 端點：POST /auth/reset-password
 *
 * 請求範例：
 * {
 *   "token": "b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a",
 *   "newPassword": "NewPassword123!"
 * }
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Data
public class ResetPasswordRequest {

    /**
     * 重置密碼 Token
     *
     * 來源：使用者點擊郵件中的連結，URL 參數中的 Token
     *
     * 驗證規則：
     * - 不能為空（@NotBlank）
     *
     * 範例：
     * - ✅ "b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a"
     * - ❌ "" （空字串）
     */
    @NotBlank(message = "Token 不能為空")
    private String token;

    /**
     * 新密碼
     *
     * 驗證規則：
     * 1. 不能為空（@NotBlank）
     * 2. 長度至少 8 個字符（@Size）
     *
     * 範例：
     * - ✅ "NewPassword123!"
     * - ✅ "MySecurePass2025"
     * - ❌ "123" （太短）
     * - ❌ "" （空字串）
     */
    @NotBlank(message = "新密碼不能為空")
    @Size(min = 8, message = "密碼長度至少 8 個字符")
    private String newPassword;
}