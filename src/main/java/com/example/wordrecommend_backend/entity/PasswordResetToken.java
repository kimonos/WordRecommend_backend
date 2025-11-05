package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 密碼重置 Token 實體
 *
 * 用途：
 * - 儲存使用者忘記密碼時的重置請求
 * - 每個 Token 對應一個使用者
 * - Token 有時效性（通常 1 小時）
 * - Token 一次性使用（使用後標記為已使用）
 *
 * 生命週期：
 * 1. 使用者請求重置密碼
 * 2. 系統生成 Token 並保存到資料庫
 * 3. 使用者收到郵件，點擊連結
 * 4. 系統驗證 Token（是否存在、是否過期、是否已使用）
 * 5. 使用者重置密碼成功
 * 6. Token 標記為已使用
 * 7. 定時任務清理過期 Token
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Entity
@Table(name = "password_reset_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    /**
     * 主鍵：Token 唯一識別碼
     *
     * 策略：IDENTITY（PostgreSQL 使用 SERIAL/BIGSERIAL）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 關聯使用者
     *
     * 關聯類型：多對一（Many-to-One）
     * - 一個使用者可以有多個 Token（例如：重複請求）
     * - 但通常我們會清理舊的未使用 Token
     *
     * 延遲載入：LAZY（預設）
     * - 只在需要時才載入使用者資訊
     *
     * 級聯操作：無
     * - Token 不應該影響使用者的生命週期
     * - 但使用者刪除時，Token 會被自動刪除（資料庫層級的 ON DELETE CASCADE）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Token 字串
     *
     * 生成方式：UUID.randomUUID() 或 SecureRandom
     * 長度：255 字符（足夠容納任何格式的 Token）
     *
     * 範例：
     * - UUID 格式：b5c8e3a7-4f2d-4c8b-9a1e-3d7f6c2b8e4a
     * - Base64 格式：xQ3vK8mP2nF7jR9tL4wZ6yH5cA1sD8eG
     *
     * 唯一性：UNIQUE 約束（資料庫層級）
     */
    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    /**
     * Token 過期時間
     *
     * 設計：通常為創建時間 + 1 小時
     * 範例：如果在 2025-11-05 08:00:00 創建，則過期時間為 2025-11-05 09:00:00
     *
     * 用途：
     * - 防止 Token 被長期濫用
     * - 增強安全性
     *
     * 檢查方式：expiryTime.isBefore(LocalDateTime.now())
     */
    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    /**
     * Token 是否已使用
     *
     * 用途：防止同一個 Token 被重複使用（重放攻擊）
     *
     * 流程：
     * - 創建時：false（未使用）
     * - 使用後：true（已使用）
     * - 已使用的 Token 無法再次使用，即使未過期
     *
     * 預設值：false
     */
    @Column(name = "used", nullable = false)
    private Boolean used = false;

    /**
     * Token 創建時間
     *
     * 自動設定：@PrePersist 會在保存前自動設定為當前時間
     *
     * 用途：
     * - 記錄 Token 何時被創建
     * - 用於日誌和審計
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Token 使用時間
     *
     * 用途：記錄密碼重置成功的時間
     *
     * 流程：
     * - 創建時：null（尚未使用）
     * - 使用後：設定為當前時間
     *
     * 可選：可以為 null
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * JPA 生命週期回調：保存前執行
     *
     * 功能：
     * - 自動設定創建時間
     * - 確保 used 欄位有預設值
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.used == null) {
            this.used = false;
        }
    }

    /**
     * 輔助方法：檢查 Token 是否過期
     *
     * @return true 如果 Token 已過期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryTime);
    }

    /**
     * 輔助方法：檢查 Token 是否有效
     *
     * 有效條件：
     * 1. 未被使用
     * 2. 未過期
     *
     * @return true 如果 Token 有效
     */
    public boolean isValid() {
        return !this.used && !isExpired();
    }

    /**
     * 輔助方法：標記 Token 為已使用
     *
     * 操作：
     * 1. 設定 used = true
     * 2. 記錄使用時間
     */
    public void markAsUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }
}