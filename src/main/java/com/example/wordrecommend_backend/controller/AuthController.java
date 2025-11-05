package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.*;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.service.AuthService;
import com.example.wordrecommend_backend.service.RefreshTokenService;
import com.example.wordrecommend_backend.service.UserService;
import com.example.wordrecommend_backend.util.CookieUtil;
import com.example.wordrecommend_backend.util.JwtUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 認證控制器
 *
 * 功能：
 * - 使用者註冊
 * - 使用者登入
 * - 使用者登出
 * - Token 刷新
 * - 忘記密碼（新增）
 * - 驗證重置 Token（新增）
 * - 重置密碼（新增）
 *
 * @author kimonos-test
 * @version 2.0（新增忘記密碼功能）
 * @since 2025-11-05
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    // ==================== 依賴注入 ====================

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenService refreshService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    // ==================== 配置參數 ====================

    @Value("${app.security.jwt-cookie-name:APP_TOKEN}")
    private String cookieName;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.cookie-samesite:Lax}")
    private String cookieSameSite;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    // ==================== 原有端點 ====================

    /**
     * 使用者註冊
     *
     * 端點：POST /auth/register
     *
     * @param registerRequest 註冊請求
     * @return 註冊成功的使用者資料
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {

        log.info("收到註冊請求: username={}, email={}",
                registerRequest.getUsername(), registerRequest.getEmail());

        try {
            User registeredUser = userService.register(registerRequest);

            log.info("✅ 註冊成功: userId={}, username={}",
                    registeredUser.getId(), registeredUser.getUsername());

            return ResponseEntity.ok(registeredUser);
        } catch (IllegalArgumentException e) {
            log.warn("❌ 註冊失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 使用者登入
     *
     * 端點：POST /auth/login
     *
     * @param loginRequest 登入請求
     * @param req HTTP 請求
     * @param res HTTP 響應
     * @return 200 OK
     */
    @PostMapping("/login")
    public ResponseEntity<Void> loginUser(@RequestBody LoginRequest loginRequest,
                                          HttpServletRequest req,
                                          HttpServletResponse res) {

        log.info("收到登入請求: identifier={}", loginRequest.getIdentifier());

        // 1) 先做帳密驗證
        AuthResponse auth = authService.login(loginRequest);

        // 2) 從 AT 取出 email
        String email = jwtUtil.extractUsername(auth.getJwt());

        // 3) 一次下 AT + RT（寫 Cookie）
        refreshTokenService.issueNewFamilyTokensForEmail(email, req, res);

        log.info("✅ 登入成功: email={}", email);

        return ResponseEntity.ok().build();
    }

    /**
     * 使用者登出
     *
     * 端點：POST /auth/logout
     *
     * @param req HTTP 請求
     * @param res HTTP 響應
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {

        log.info("收到登出請求");

        // 撤銷目前這顆 RT（若存在）
        refreshTokenService.revokeCurrentRtIfPresent(req);

        // 交由 Service 清除 AT/RT
        refreshTokenService.clearBothCookies(res);

        // 額外：清 session 與 JSESSIONID
        var session = req.getSession(false);
        if (session != null) session.invalidate();
        CookieUtil.clearCookie(res, "JSESSIONID", cookieSecure, cookieSameSite, "/");

        log.info("✅ 登出成功");

        return ResponseEntity.noContent().build();
    }

    /**
     * Token 刷新
     *
     * 端點：POST /auth/refresh
     *
     * @param req HTTP 請求
     * @param res HTTP 響應
     * @return 200 OK
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest req, HttpServletResponse res) {

        log.info("收到 Token 刷新請求");

        refreshService.refreshAndRotate(req, res);

        log.info("✅ Token 刷新成功");

        return ResponseEntity.ok().build();
    }

    // ==================== 忘記密碼端點（新增）====================

    /**
     * 忘記密碼 - 請求重置密碼
     *
     * 端點：POST /auth/forgot-password
     *
     * 功能：
     * - 接收使用者的 Email
     * - 驗證 Email 是否存在（內部處理，不透露給前端）
     * - 生成重置 Token
     * - 發送重置郵件
     *
     * 請求範例：
     * {
     *   "email": "user@example.com"
     * }
     *
     * 響應範例（統一）：
     * {
     *   "message": "如果該 Email 已註冊，您將收到重置密碼的郵件。請檢查您的郵箱（包括垃圾郵件資料夾）。"
     * }
     *
     * 安全設計：
     * - 無論 Email 是否存在，都返回相同訊息（防止帳號探測）
     * - 不透露 Email 是否已註冊
     * - 郵件發送在異步線程中執行（不阻塞響應）
     *
     * @param request 忘記密碼請求（包含 Email）
     * @return 統一的成功訊息
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {

        log.info("🔵 收到忘記密碼請求: email={}", request.getEmail());

        try {
            // 調用 Service 處理
            authService.requestPasswordReset(request.getEmail());

            log.info("✅ 忘記密碼請求處理完成: email={}", request.getEmail());

        } catch (MessagingException e) {
            // 郵件發送失敗
            log.error("❌ 郵件發送失敗: email={}, error={}", request.getEmail(), e.getMessage());

            // 🔑 安全設計：即使郵件發送失敗，也返回統一訊息
            // 不透露具體錯誤給使用者（防止攻擊者利用錯誤訊息）
        }

        // 🔑 統一返回成功訊息（無論 Email 是否存在、郵件是否發送成功）
        String message = "如果該 Email 已註冊，您將收到重置密碼的郵件。請檢查您的郵箱（包括垃圾郵件資料夾）。";

        return ResponseEntity.ok(new MessageResponse(message));
    }

    /**
     * 驗證重置密碼 Token（可選端點）
     *
     * 端點：GET /auth/reset-password/validate?token=xxx
     *
     * 功能：
     * - 前端在顯示重置密碼頁面前，先驗證 Token 是否有效
     * - 如果 Token 無效，前端顯示錯誤訊息，不顯示密碼輸入框
     *
     * 請求範例：
     * GET /auth/reset-password/validate?token=b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a
     *
     * 響應範例：
     * {
     *   "valid": true
     * }
     *
     * 或
     *
     * {
     *   "valid": false
     * }
     *
     * 用途：
     * - 改善使用者體驗（提前驗證，避免填寫密碼後才發現 Token 無效）
     * - 可選端點（如果前端不需要提前驗證，可以省略此端點）
     *
     * @param token 重置密碼 Token
     * @return Token 是否有效
     */
    @GetMapping("/reset-password/validate")
    public ResponseEntity<ValidateTokenResponse> validateResetToken(@RequestParam String token) {

        log.info("🔵 收到驗證 Token 請求: token={}", token);

        boolean isValid = authService.validateResetToken(token);

        log.info("Token 驗證結果: token={}, valid={}", token, isValid);

        return ResponseEntity.ok(new ValidateTokenResponse(isValid));
    }

    /**
     * 重置密碼
     *
     * 端點：POST /auth/reset-password
     *
     * 功能：
     * - 驗證 Token
     * - 更新密碼
     * - 標記 Token 為已使用
     *
     * 請求範例：
     * {
     *   "token": "b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a",
     *   "newPassword": "NewPassword123!"
     * }
     *
     * 響應範例（成功）：
     * {
     *   "message": "密碼重置成功！請使用新密碼登入。"
     * }
     *
     * 響應範例（失敗）：
     * 400 Bad Request
     * {
     *   "message": "Token 無效或已過期"
     * }
     *
     * 流程：
     * 1. 驗證請求參數（@Valid）
     * 2. 調用 Service 重置密碼
     * 3. 返回成功訊息
     *
     * 錯誤處理：
     * - Token 不存在 → 400
     * - Token 已使用 → 400
     * - Token 已過期 → 400
     * - 其他錯誤 → 500
     *
     * @param request 重置密碼請求（包含 Token 和新密碼）
     * @return 成功訊息或錯誤訊息
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {

        log.info("🔵 收到重置密碼請求: token={}", request.getToken());

        try {
            // 調用 Service 重置密碼
            authService.resetPassword(request.getToken(), request.getNewPassword());

            log.info("✅ 密碼重置成功: token={}", request.getToken());

            // 返回成功訊息
            String message = "密碼重置成功！請使用新密碼登入。";
            return ResponseEntity.ok(new MessageResponse(message));

        } catch (RuntimeException e) {
            // Token 無效、已使用、已過期等錯誤
            log.warn("❌ 密碼重置失敗: token={}, error={}", request.getToken(), e.getMessage());

            // 返回 400 Bad Request
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new MessageResponse(e.getMessage()));
        }
    }

    // ==================== 內部類別 ====================

    /**
     * 簡單訊息用的小 DTO
     *
     * 用途：
     * - 返回簡單的文字訊息
     * - 適用於各種成功/失敗回應
     *
     * 範例：
     * {
     *   "message": "操作成功"
     * }
     *
     * @deprecated 請使用 com.example.wordrecommend_backend.dto.MessageResponse
     */
    @Deprecated
    public record SimpleMessage(String message) {}
}