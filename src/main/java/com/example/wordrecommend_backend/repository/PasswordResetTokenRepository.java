package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.PasswordResetToken;
import com.example.wordrecommend_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 密碼重置 Token Repository
 *
 * 功能：
 * - 根據 Token 字串查詢 Token
 * - 根據使用者查詢 Token
 * - 刪除過期的 Token（定時清理）
 * - 刪除使用者的所有未使用 Token（防止重複請求）
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * 根據 Token 字串查詢 Token
     *
     * 用途：
     * - 使用者點擊郵件中的連結時，後端驗證 Token
     *
     * 流程：
     * 1. 前端從 URL 取得 Token（例如：?token=xxx）
     * 2. 後端調用此方法查詢 Token
     * 3. 檢查 Token 是否存在、是否過期、是否已使用
     *
     * @param token Token 字串
     * @return Optional<PasswordResetToken> 如果找到則返回 Token，否則返回 empty
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * 根據使用者查詢所有 Token
     *
     * 用途：
     * - 檢查使用者是否有未使用的 Token
     * - 防止重複請求（可以先刪除舊的未使用 Token）
     *
     * @param user 使用者實體
     * @return List<PasswordResetToken> Token 列表
     */
    List<PasswordResetToken> findByUser(User user);

    /**
     * 根據使用者和已使用狀態查詢 Token
     *
     * 用途：
     * - 查詢使用者所有未使用的 Token
     * - 在生成新 Token 前，可以先刪除這些 Token
     *
     * 範例：
     * - findByUserAndUsed(user, false) → 查詢所有未使用的 Token
     * - findByUserAndUsed(user, true) → 查詢所有已使用的 Token
     *
     * @param user 使用者實體
     * @param used 是否已使用
     * @return List<PasswordResetToken> Token 列表
     */
    List<PasswordResetToken> findByUserAndUsed(User user, Boolean used);

    /**
     * 刪除過期的 Token（定時清理任務使用）
     *
     * 用途：
     * - 每天清理過期的 Token，釋放資料庫空間
     * - 提升查詢效能
     *
     * 流程：
     * 1. 定時任務（例如：每天凌晨 2 點）觸發
     * 2. 調用此方法刪除所有過期的 Token
     * 3. 返回刪除的數量
     *
     * @Modifying 註解：表示這是一個修改操作（DELETE）
     * @Query 註解：自定義 JPQL 查詢
     *
     * @param now 當前時間
     * @return int 刪除的 Token 數量
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryTime < :now")
    int deleteByExpiryTimeBefore(@Param("now") LocalDateTime now);

    /**
     * 刪除使用者所有未使用的 Token
     *
     * 用途：
     * - 使用者重複請求重置密碼時，先刪除舊的未使用 Token
     * - 確保每個使用者只有一個有效的 Token
     *
     * 流程：
     * 1. 使用者請求重置密碼
     * 2. 調用此方法刪除舊的未使用 Token
     * 3. 生成新的 Token
     *
     * @param user 使用者實體
     * @return int 刪除的 Token 數量
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = :user AND t.used = false")
    int deleteUnusedTokensByUser(@Param("user") User user);

    /**
     * 檢查使用者是否有有效的 Token
     *
     * 用途：
     * - 防止使用者在短時間內重複請求
     * - 提示使用者「已發送重置郵件，請檢查郵箱」
     *
     * 有效 Token 條件：
     * 1. 未使用（used = false）
     * 2. 未過期（expiryTime > now）
     *
     * @param user 使用者實體
     * @param now 當前時間
     * @return boolean 是否存在有效的 Token
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
            "FROM PasswordResetToken t " +
            "WHERE t.user = :user AND t.used = false AND t.expiryTime > :now")
    boolean existsValidTokenForUser(@Param("user") User user, @Param("now") LocalDateTime now);
}