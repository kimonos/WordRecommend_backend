package com.example.wordrecommend_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘記密碼請求 DTO
 *
 * 用途：
 * - 使用者輸入 Email 請求重置密碼
 *
 * 端點：POST /auth/forgot-password
 *
 * 請求範例：
 * {
 *   "email": "user@example.com"
 * }
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Data
public class ForgotPasswordRequest {

    /**
     * 使用者的 Email
     *
     * 驗證規則：
     * 1. 不能為空（@NotBlank）
     * 2. 必須是有效的 Email 格式（@Email）
     *
     * 範例：
     * - ✅ "user@example.com"
     * - ✅ "test.user+filter@gmail.com"
     * - ❌ "" （空字串）
     * - ❌ "invalid-email" （無效格式）
     */
    @NotBlank(message = "Email 不能為空")
    @Email(message = "Email 格式不正確")
    private String email;
}