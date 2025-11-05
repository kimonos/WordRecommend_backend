package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.AuthResponse;
import com.example.wordrecommend_backend.dto.LoginRequest;
import com.example.wordrecommend_backend.entity.PasswordResetToken;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.repository.PasswordResetTokenRepository;
import com.example.wordrecommend_backend.repository.UserRepository;
import com.example.wordrecommend_backend.util.JwtUtil;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 認證服務
 *
 * 功能：
 * - 使用者登入
 * - 忘記密碼
 * - 重置密碼
 *
 * @author kimonos-test
 * @version 2.0（新增忘記密碼功能）
 * @since 2025-11-05
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // ==================== Logger ====================

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // ==================== 依賴注入 ====================

    /**
     * Spring Security 認證管理器
     * 用途：驗證使用者帳號密碼
     */
    private final AuthenticationManager authenticationManager;

    /**
     * 使用者 Repository
     * 用途：查詢使用者資料
     */
    private final UserRepository userRepository;

    /**
     * JWT 工具類
     * 用途：生成和驗證 JWT Token
     */
    private final JwtUtil jwtUtil;

    /**
     * 密碼重置 Token Repository
     * 用途：管理密碼重置 Token
     */
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * 郵件服務
     * 用途：發送密碼重置郵件
     */
    private final EmailService emailService;

    /**
     * 密碼編碼器（用於加密密碼）
     * 用途：加密新密碼
     */
    private final PasswordEncoder passwordEncoder;

    // ==================== 配置參數 ====================

    /**
     * Token 過期時間（小時）
     *
     * 來源：application.properties
     * 預設值：1 小時
     *
     * 配置項：password-reset.token-expiry-hours
     */
    @Value("${password-reset.token-expiry-hours:1}")
    private int tokenExpiryHours;

    // ==================== 登入功能（原有功能）====================

    /**
     * 使用者登入
     *
     * 功能：
     * - 驗證帳號密碼
     * - 生成 JWT Token
     *
     * @param loginRequest 登入請求（包含 identifier 和 password）
     * @return AuthResponse 包含 JWT Token
     * @throws AuthenticationException 認證失敗時拋出
     */
    public AuthResponse login(LoginRequest loginRequest) {

        log.info("🔵 使用者登入請求: identifier={}", loginRequest.getIdentifier());

        // ========== 步驟 0：正規化輸入 ==========

        final String id = (loginRequest.getIdentifier() == null ? "" : loginRequest.getIdentifier().trim());
        final String pwd = (loginRequest.getPassword() == null ? "" : loginRequest.getPassword());

        // ========== 步驟 1：驗證帳密（username 或 email 都可）==========

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(id, pwd));
            log.debug("認證成功: identifier={}", id);
        } catch (AuthenticationException e) {
            log.warn("❌ 認證失敗: identifier={}, error={}", id, e.getMessage());
            throw e;
        }

        // ========== 步驟 2：推導 email（若 id 含 '@' 視為 email；否則用 username 查出 email）==========

        final String email = id.contains("@")
                ? id.toLowerCase()
                : userRepository.findByUsernameIgnoreCase(id)
                .map(u -> u.getEmail().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.debug("Email 推導成功: email={}", email);

        // ========== 步驟 3：以 email 當 subject 簽 Access Token ==========

        final String jwt = jwtUtil.generateTokenFromEmail(email);

        log.info("✅ 登入成功: email={}", email);

        // ========== 步驟 4：回傳 ==========

        return new AuthResponse(jwt);
    }

    // ==================== 忘記密碼功能（新增）====================

    /**
     * 請求重置密碼（v1.0）
     *
     * 功能：
     * - 驗證 Email 是否存在
     * - 生成重置密碼 Token
     * - 發送重置郵件
     *
     * 安全設計：
     * - 無論 Email 是否存在，都返回相同訊息（防止帳號探測）
     * - 刪除舊的未使用 Token（防止重複請求）
     * - Token 有時效性（1 小時）
     * - Token 一次性使用
     *
     * 流程：
     * 1. 查詢使用者（根據 Email）
     * 2. 如果使用者不存在：記錄日誌，模擬延遲，返回（不透露資訊）
     * 3. 如果使用者存在：
     *    a. 檢查是否有有效 Token（防止短時間內重複請求）
     *    b. 刪除舊的未使用 Token
     *    c. 生成新的 Token
     *    d. 保存到資料庫
     *    e. 發送郵件
     * 4. 返回（統一返回成功，不透露 Email 是否存在）
     *
     * @param email 使用者的 Email
     * @throws MessagingException 郵件發送失敗時拋出
     */
    @Transactional
    public void requestPasswordReset(String email) throws MessagingException {

        log.info("🔵 收到密碼重置請求: email={}", email);

        // ========== 步驟 1：查詢使用者 ==========

        Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email.toLowerCase());

        // ========== 步驟 2：安全檢查（防止帳號探測）==========

        if (optionalUser.isEmpty()) {
            // 🔑 安全設計：即使使用者不存在，也不透露此資訊

            log.warn("⚠️ 密碼重置請求的 Email 不存在: email={}", email);

            // 模擬正常處理時間（防止透過響應時間判斷 Email 是否存在）
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 返回（不拋出異常，不透露 Email 不存在）
            log.info("✅ 密碼重置請求處理完成（Email 不存在，但返回成功）: email={}", email);
            return;
        }

        User user = optionalUser.get();

        log.debug("使用者找到: userId={}, username={}", user.getId(), user.getUsername());

        // ========== 步驟 3：檢查是否有有效的 Token（防止重複請求）==========

        LocalDateTime now = LocalDateTime.now();
        boolean hasValidToken = passwordResetTokenRepository.existsValidTokenForUser(user, now);

        if (hasValidToken) {
            // 如果使用者已經有有效的 Token，直接返回
            // 可以選擇：
            // 1. 直接返回（不發送新郵件）✅ 採用此方案
            // 2. 重新發送郵件（使用舊 Token）
            // 3. 刪除舊 Token，生成新 Token

            log.warn("⚠️ 使用者已有有效的重置 Token: userId={}, email={}", user.getId(), email);

            // 這裡選擇方案 1：直接返回
            log.info("✅ 密碼重置請求處理完成（已有有效 Token）: email={}", email);
            return;
        }

        // ========== 步驟 4：刪除舊的未使用 Token ==========

        int deletedCount = passwordResetTokenRepository.deleteUnusedTokensByUser(user);

        if (deletedCount > 0) {
            log.debug("刪除了 {} 個舊的未使用 Token: userId={}", deletedCount, user.getId());
        }

        // ========== 步驟 5：生成新的 Token ==========

        String token = generateResetToken();

        log.debug("生成新 Token: userId={}, token={}", user.getId(), token);

        // ========== 步驟 6：計算過期時間 ==========

        LocalDateTime expiryTime = now.plusHours(tokenExpiryHours);

        log.debug("Token 過期時間: {}", expiryTime);

        // ========== 步驟 7：保存 Token 到資料庫 ==========

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiryTime(expiryTime);
        resetToken.setUsed(false);

        passwordResetTokenRepository.save(resetToken);

        log.info("✅ Token 已保存: userId={}, tokenId={}", user.getId(), resetToken.getId());

        // ========== 步驟 8：發送郵件 ==========

        try {
            // 🔑 修改這裡：添加第 4 個參數 expiryTime
            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getUsername(),
                    token,
                    expiryTime  // 新增
            );

            log.info("✅ 密碼重置郵件已發送: email={}", email);
        } catch (MessagingException e) {
            log.error("❌ 郵件發送失敗: email={}, error={}", email, e.getMessage());
            throw e;
        }

        log.info("✅ 密碼重置請求處理完成: email={}", email);
    }

    /**
     * 生成安全的重置密碼 Token
     *
     * 生成方式：UUID（通用唯一識別碼）
     *
     * 特點：
     * - 隨機、不可預測
     * - 全球唯一
     * - URL 安全
     *
     * 範例：b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a
     *
     * 替代方案（更安全）：
     * - 使用 SecureRandom + Base64 編碼
     * - 長度更長（32-64 字節）
     *
     * @return String Token 字串
     */
    private String generateResetToken() {
        // 方案 1：使用 UUID（簡單、夠安全）
        return UUID.randomUUID().toString();

        // 或

        // 方案 2：使用 SecureRandom（更安全，但代碼較複雜）
        // SecureRandom random = new SecureRandom();
        // byte[] bytes = new byte[32];
        // random.nextBytes(bytes);
        // return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 驗證重置密碼 Token
     *
     * 功能：
     * - 檢查 Token 是否存在
     * - 檢查 Token 是否過期
     * - 檢查 Token 是否已使用
     *
     * 用途：
     * - 前端在顯示重置密碼頁面前，先驗證 Token 是否有效
     *
     * @param token Token 字串
     * @return boolean Token 是否有效
     */
    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {

        log.info("🔵 驗證重置 Token: token={}", token);

        // ========== 步驟 1：查詢 Token ==========

        Optional<PasswordResetToken> optionalToken = passwordResetTokenRepository.findByToken(token);

        if (optionalToken.isEmpty()) {
            log.warn("⚠️ Token 不存在: token={}", token);
            return false;
        }

        PasswordResetToken resetToken = optionalToken.get();

        log.debug("Token 找到: tokenId={}, userId={}", resetToken.getId(), resetToken.getUser().getId());

        // ========== 步驟 2：檢查是否已使用 ==========

        if (resetToken.getUsed()) {
            log.warn("⚠️ Token 已被使用: token={}, usedAt={}", token, resetToken.getUsedAt());
            return false;
        }

        // ========== 步驟 3：檢查是否過期 ==========

        if (resetToken.isExpired()) {
            log.warn("⚠️ Token 已過期: token={}, expiryTime={}", token, resetToken.getExpiryTime());
            return false;
        }

        // ========== Token 有效 ==========

        log.info("✅ Token 有效: token={}", token);
        return true;
    }

    /**
     * 重置密碼（v1.0）
     *
     * 功能：
     * - 驗證 Token
     * - 更新密碼
     * - 標記 Token 為已使用
     *
     * 流程：
     * 1. 查詢 Token
     * 2. 驗證 Token（是否存在、是否過期、是否已使用）
     * 3. 查詢使用者
     * 4. 更新密碼（加密）
     * 5. 標記 Token 為已使用
     * 6. 保存
     *
     * 安全設計：
     * - 密碼必須加密（BCrypt）
     * - Token 使用後標記為已使用（防止重複使用）
     * - 事務處理（確保原子性）
     *
     * @param token Token 字串
     * @param newPassword 新密碼（明文）
     * @throws RuntimeException Token 無效時拋出
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {

        log.info("🔵 重置密碼: token={}", token);

        // ========== 步驟 1：查詢 Token ==========

        Optional<PasswordResetToken> optionalToken = passwordResetTokenRepository.findByToken(token);

        if (optionalToken.isEmpty()) {
            log.warn("❌ Token 不存在: token={}", token);
            throw new RuntimeException("Token 無效或已過期");
        }

        PasswordResetToken resetToken = optionalToken.get();

        log.debug("Token 找到: tokenId={}, userId={}", resetToken.getId(), resetToken.getUser().getId());

        // ========== 步驟 2：驗證 Token ==========

        // 檢查是否已使用
        if (resetToken.getUsed()) {
            log.warn("❌ Token 已被使用: token={}, usedAt={}", token, resetToken.getUsedAt());
            throw new RuntimeException("Token 已被使用");
        }

        // 檢查是否過期
        if (resetToken.isExpired()) {
            log.warn("❌ Token 已過期: token={}, expiryTime={}", token, resetToken.getExpiryTime());
            throw new RuntimeException("Token 已過期");
        }

        // ========== 步驟 3：查詢使用者 ==========

        User user = resetToken.getUser();

        log.debug("使用者找到: userId={}, username={}", user.getId(), user.getUsername());

        // ========== 步驟 4：更新密碼（加密）==========

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        userRepository.save(user);

        log.info("✅ 密碼已更新: userId={}", user.getId());

        // ========== 步驟 5：標記 Token 為已使用 ==========

        resetToken.markAsUsed();
        passwordResetTokenRepository.save(resetToken);

        log.info("✅ Token 已標記為已使用: tokenId={}", resetToken.getId());

        log.info("✅ 密碼重置完成: userId={}, username={}", user.getId(), user.getUsername());
    }
}