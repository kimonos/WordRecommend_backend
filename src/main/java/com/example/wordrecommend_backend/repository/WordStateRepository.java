package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WordStateRepository extends JpaRepository<WordState, Long> {

    // ==================== v1.0 原有方法 ====================

    /**
     * 查詢使用者對特定單字的學習狀態
     */
    Optional<WordState> findByUserAndWord(User user, Word word);

    /**
     * 查詢使用者在指定狀態的單字（隨機排序）
     */
    @Query("SELECT ws FROM WordState ws WHERE ws.user = :user AND ws.currentState = :state ORDER BY function('RANDOM')")
    List<WordState> findByUserAndState(@Param("user") User user, @Param("state") String state, Pageable pageable);

    /**
     * 統計使用者在指定狀態的單字數量
     */
    @Query("SELECT COUNT(ws) FROM WordState ws WHERE ws.user = :user AND ws.currentState = :state")
    long countByUserAndState(@Param("user") User user, @Param("state") String state);

    // ==================== v2.0 新增方法 ====================

    /**
     * 查詢所有 S-1 狀態的單字（遺忘單字）
     *
     * 用途：
     * - Phase 5：優先推薦遺忘單字
     * - 統計報告：「你有 5 個單字已遺忘」
     *
     * 排序：按遺忘時間排序（最近遺忘的優先）
     *
     * @param user 目標使用者
     * @param pageable 分頁參數
     * @return S-1 狀態的單字列表
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user AND ws.currentState = 'S-1' " +
            "ORDER BY ws.lastForgottenTime DESC")
    List<WordState> findForgottenWords(@Param("user") User user, Pageable pageable);

    /**
     * 查詢所有 S-1 狀態的單字數量
     *
     * 用途：
     * - 統計報告
     * - UI 顯示（紅色徽章）
     *
     * @param user 目標使用者
     * @return S-1 狀態的單字數量
     */
    @Query("SELECT COUNT(ws) FROM WordState ws " +
            "WHERE ws.user = :user AND ws.currentState = 'S-1'")
    long countForgottenWords(@Param("user") User user);

    /**
     * 查詢指定多個狀態的單字（批次查詢）
     *
     * 用途：
     * - Phase 5：同時取 S1, S2, S3 做複習推薦
     * - 靈活組合查詢
     *
     * 範例：
     * <pre>
     * List<String> states = Arrays.asList("S1", "S2", "S-1");
     * List<WordState> results = repository.findByUserAndCurrentStateIn(user, states);
     * </pre>
     *
     * @param user 目標使用者
     * @param states 狀態列表（如 ["S1", "S2", "S3"]）
     * @param pageable 分頁參數
     * @return 符合條件的單字狀態列表
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user AND ws.currentState IN :states " +
            "ORDER BY ws.lastReviewTime ASC")
    List<WordState> findByUserAndCurrentStateIn(
            @Param("user") User user,
            @Param("states") List<String> states,
            Pageable pageable
    );

    /**
     * 查詢所有學習過的單字（排除 S0）
     *
     * 用途：
     * - Phase 5：計算推薦優先度（需要所有學習過的單字）
     * - 統計報告：「你已學習 120 個單字」
     *
     * 注意：此方法可能返回大量資料，建議加分頁
     *
     * @param user 目標使用者
     * @return 所有 has_ever_learned = true 的單字
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user AND ws.hasEverLearned = true")
    List<WordState> findAllLearnedWords(@Param("user") User user);

    /**
     * 查詢所有學習過的單字（分頁版本）
     *
     * @param user 目標使用者
     * @param pageable 分頁參數
     * @return 分頁結果
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user AND ws.hasEverLearned = true " +
            "ORDER BY ws.lastReviewTime DESC")
    List<WordState> findAllLearnedWords(@Param("user") User user, Pageable pageable);

    /**
     * 查詢最近更新的單字（用於快取優化）
     *
     * 用途：
     * - Phase 6：答題後快速查詢相關單字
     * - 增量更新快取
     *
     * @param user 目標使用者
     * @param since 起始時間（查詢此時間之後更新的單字）
     * @return 最近更新的單字列表
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user AND ws.lastReviewTime >= :since " +
            "ORDER BY ws.lastReviewTime DESC")
    List<WordState> findRecentlyReviewedWords(
            @Param("user") User user,
            @Param("since") LocalDateTime since
    );

    /**
     * 查詢需要緊急複習的單字
     *
     * 條件：
     * - 記憶強度 < 0.3（臨界值）
     * - 距離上次複習 > 7 天
     * - 排除 S0 和 S-1（新單字和已遺忘單字）
     *
     * 用途：
     * - Phase 5：優先推薦即將遺忘的單字
     * - 主動推送通知
     *
     * @param user 目標使用者
     * @param threshold 記憶強度閾值（預設 0.3）
//     * @param daysSince 距離上次複習的天數（預設 7）
     * @param pageable 分頁參數
     * @return 需要緊急複習的單字列表
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user " +
            "AND ws.memoryStrength < :threshold " +
            "AND ws.currentState NOT IN ('S0', 'S-1') " +
            "AND ws.lastReviewTime < :since " +
            "ORDER BY ws.memoryStrength ASC")
    List<WordState> findUrgentReviewWords(
            @Param("user") User user,
            @Param("threshold") double threshold,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /**
     * 統計各狀態的單字數量（批次統計）
     *
     * 用途：
     * - 學習報告：一次查詢所有狀態統計
     * - UI 儀表板
     *
     * 返回格式：List<Object[]>
     * - Object[0]: String state（狀態名稱）
     * - Object[1]: Long count（數量）
     *
     * 範例：
     * <pre>
     * List<Object[]> stats = repository.countByUserGroupByState(user);
     * for (Object[] row : stats) {
     *     String state = (String) row[0];
     *     Long count = (Long) row[1];
     *     System.out.println(state + ": " + count);
     * }
     * // 輸出：
     * // S0: 1000
     * // S1: 50
     * // S2: 30
     * // S3: 20
     * // S-1: 5
     * </pre>
     *
     * @param user 目標使用者
     * @return 狀態統計列表
     */
    @Query("SELECT ws.currentState, COUNT(ws) FROM WordState ws " +
            "WHERE ws.user = :user " +
            "GROUP BY ws.currentState")
    List<Object[]> countByUserGroupByState(@Param("user") User user);

    /**
     * 查詢困難單字（個人化困難度）
     *
     * 條件：
     * - forgotten_count >= 2（遺忘 2 次以上）
     * - 或 accuracy_rate < 0.5（答對率低於 50%）
     *
     * 用途：
     * - 統計報告：「你的困難單字」
     * - 特殊推薦策略
     *
     * @param user 目標使用者
     * @param pageable 分頁參數
     * @return 困難單字列表
     */
    @Query("SELECT ws FROM WordState ws " +
            "WHERE ws.user = :user " +
            "AND ws.hasEverLearned = true " +
            "AND (ws.forgottenCount >= 2 " +
            "     OR (ws.totalCorrect + ws.totalIncorrect >= 5 " +
            "         AND ws.totalCorrect * 1.0 / (ws.totalCorrect + ws.totalIncorrect) < 0.5)) " +
            "ORDER BY ws.forgottenCount DESC, ws.lastReviewTime ASC")
    List<WordState> findDifficultWords(@Param("user") User user, Pageable pageable);

    /**
     * 根據使用者查詢所有 WordState（衰減任務用）
     *
     * @param user 使用者
     * @return 該使用者的所有 WordState
     */
    List<WordState> findByUser(User user);


}