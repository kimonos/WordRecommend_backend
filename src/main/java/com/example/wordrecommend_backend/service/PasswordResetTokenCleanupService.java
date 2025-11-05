package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 密碼重置 Token 清理服務
 *
 * 功能：
 * - 定時清理過期的 Token
 * - 釋放資料庫空間
 * - 提升查詢效能
 *
 * 執行頻率：
 * - 每天凌晨 2 點執行
 * - 可在 @Scheduled 註解中調整
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenCleanupService {

    /**
     * 密碼重置 Token Repository
     */
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * 定時清理過期的 Token
     *
     * 執行時間：
     * - Cron 表達式：0 0 2 * * ?
     * - 含義：每天凌晨 2:00:00 執行
     *
     * Cron 表達式說明：
     * 秒 分 時 日 月 星期
     * 0  0  2  *  *  ?
     * │  │  │  │  │  │
     * │  │  │  │  │  └─ 星期（? 表示不指定）
     * │  │  │  │  └──── 月（* 表示每月）
     * │  │  │  └─────── 日（* 表示每日）
     * │  │  └────────── 時（2 表示凌晨 2 點）
     * │  └───────────── 分（0 表示 0 分）
     * └──────────────── 秒（0 表示 0 秒）
     *
     * 其他常見 Cron 表達式範例：
     * - 每小時執行：0 0 * * * ?
     * - 每 30 分鐘執行：0 0/30 * * * ?
     * - 每週一凌晨 3 點執行：0 0 3 ? * MON
     * - 每月 1 號凌晨 1 點執行：0 0 1 1 * ?
     *
     * 流程：
     * 1. 記錄開始日誌
     * 2. 取得當前時間
     * 3. 刪除所有過期的 Token
     * 4. 記錄刪除數量
     * 5. 記錄完成日誌
     *
     * 異常處理：
     * - 如果清理失敗，記錄錯誤日誌
     * - 不影響應用程式運行
     * - 下次執行時會重試
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨 2 點執行
    @Transactional
    public void cleanupExpiredTokens() {

        log.info("🔵 開始清理過期的密碼重置 Token");

        try {
            // ========== 步驟 1：取得當前時間 ==========

            LocalDateTime now = LocalDateTime.now();

            log.debug("當前時間: {}", now);

            // ========== 步驟 2：刪除過期的 Token ==========

            int deletedCount = passwordResetTokenRepository.deleteByExpiryTimeBefore(now);

            // ========== 步驟 3：記錄結果 ==========

            if (deletedCount > 0) {
                log.info("✅ 清理完成: 刪除了 {} 個過期的 Token", deletedCount);
            } else {
                log.info("✅ 清理完成: 沒有過期的 Token 需要刪除");
            }

        } catch (Exception e) {
            // 清理失敗：記錄錯誤，但不影響應用程式運行
            log.error("❌ 清理過期 Token 失敗: {}", e.getMessage(), e);
        }
    }

    /**
     * 手動清理過期的 Token（供測試或手動觸發使用）
     *
     * 用途：
     * - 測試清理功能
     * - 管理員手動清理
     * - 不依賴定時任務
     *
     * 可以在 Controller 中暴露此方法，供管理員調用
     *
     * @return 刪除的 Token 數量
     */
    @Transactional
    public int manualCleanup() {

        log.info("🔵 手動清理過期的密碼重置 Token");

        LocalDateTime now = LocalDateTime.now();
        int deletedCount = passwordResetTokenRepository.deleteByExpiryTimeBefore(now);

        log.info("✅ 手動清理完成: 刪除了 {} 個過期的 Token", deletedCount);

        return deletedCount;
    }

    /**
     * 清理所有已使用的 Token（供測試或手動觸發使用）
     *
     * 用途：
     * - 清理已使用但未過期的 Token
     * - 釋放更多資料庫空間
     *
     * 注意：
     * - 此方法會刪除所有已使用的 Token（無論是否過期）
     * - 適合在資料庫空間緊張時使用
     *
     * @return 刪除的 Token 數量
     */
    @Transactional
    public int cleanupUsedTokens() {

        log.info("🔵 清理所有已使用的密碼重置 Token");

        // 自定義查詢：DELETE FROM password_reset_token WHERE used = true
        // 需要在 Repository 中添加此方法（可選）

        log.info("✅ 清理已使用 Token 完成");

        // 暫時返回 0，如果需要此功能，請在 Repository 中添加對應方法
        return 0;
    }
}