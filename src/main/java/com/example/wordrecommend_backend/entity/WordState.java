package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 單字學習狀態實體（v2.1 - 統一記憶模型）
 *
 * 學習路徑：
 * 1. 所有學習都從「閱讀」開始
 * 2. 閱讀 ≥ 5 秒 → 標記為已學習（has_ever_learned = true）
 * 3. 後續可透過「繼續閱讀」或「答題」提升記憶強度
 *
 * 記憶強度貢獻：
 * - 閱讀：+0.03~0.05 / 次（被動學習）
 * - 答題：+0.1~0.3 / 次（主動學習）
 *
 * 狀態轉移規則：
 * - S0: 從未學習 (memoryStrength = 0, hasEverLearned = false)
 * - S1: 初學階段 (0 < memoryStrength < threshold_s2)
 * - S2: 熟悉階段 (threshold_s2 <= memoryStrength < threshold_s3)
 * - S3: 精通階段 (memoryStrength >= threshold_s3)
 * - S-1: 完全遺忘 (memoryStrength = 0, hasEverLearned = true)
 *
 * @author kimonos-test
 * @version 2.1
 * @since 2025-11-03
 */
@Entity
@Table(name = "word_state")
@Data
@NoArgsConstructor
public class WordState {

    // ==================== 主鍵 ====================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== 關聯關係 ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    // ==================== 核心演算法欄位（v1.0）====================

    /**
     * 記憶強度 M_i(t) - 統一記憶模型
     * 範圍：[0.0, 1.0]
     *
     * 貢獻來源：
     * - 閱讀行為：+0.03~0.05 / 次（被動學習）
     * - 答題行為：+0.1~0.3 / 次（主動學習）
     * - 時間衰減：× e^(-λΔt)
     */
    @Column(name = "memory_strength", nullable = false)
    private Double memoryStrength;

    /**
     * FSM 離散狀態
     * 允許值：S0, S-1, S1, S2, S3
     */
    @Column(name = "current_state", nullable = false)
    private String currentState;

    /**
     * 上次複習時間（用於計算遺忘曲線的 Δt）
     */
    @Column(name = "last_review_time", nullable = false)
    private LocalDateTime lastReviewTime;

    /**
     * 計算出的推薦優先度 P_i
     * 越大越優先推薦
     */
    @Column(name = "next_review_priority")
    private Double nextReviewPriority;

    // ==================== 閱讀統計欄位（v1.0）====================

    /**
     * 最後閱讀時間
     */
    @Column(name = "last_read_time")
    private LocalDateTime lastReadTime;

    /**
     * 累計閱讀次數（被動學習行為）
     */
    @Column(name = "read_count", nullable = false)
    private Integer readCount = 0;

    /**
     * 累計閱讀時長（秒）
     */
    @Column(name = "total_read_duration", nullable = false)
    private Double totalReadDuration = 0.0;

    /**
     * 平均閱讀時長（秒）
     */
    @Column(name = "avg_read_duration", nullable = false)
    private Double avgReadDuration = 0.0;

    // ==================== 學習歷史追蹤欄位（v2.0）====================

    /**
     * 🔑 是否曾經學習過（區分 S0 和 S-1）
     *
     * 觸發條件：閱讀時長 ≥ 5 秒
     * 業務規則：一旦設為 true，永不改回 false
     */
    @Column(name = "has_ever_learned", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean hasEverLearned = false;

    /**
     * 首次學習時間（首次有效閱讀的時間）
     */
    @Column(name = "first_learn_time")
    private LocalDateTime firstLearnTime;

    /**
     * 累計複習次數（包含閱讀和答題）
     */
    @Column(name = "total_review_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalReviewCount = 0;

    /**
     * 遺忘次數（記憶強度降至 0 的累計次數）
     */
    @Column(name = "forgotten_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer forgottenCount = 0;

    /**
     * 上次遺忘時間（用於計算復原優先度）
     */
    @Column(name = "last_forgotten_time")
    private LocalDateTime lastForgottenTime;

    // ==================== 答題統計欄位（v2.0）====================

    /**
     * 連續答對次數（用於判斷狀態晉級）
     * 答錯時歸零
     */
    @Column(name = "consecutive_correct", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer consecutiveCorrect = 0;

    /**
     * 累計答對次數
     */
    @Column(name = "total_correct", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalCorrect = 0;

    /**
     * 累計答錯次數
     */
    @Column(name = "total_incorrect", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalIncorrect = 0;

    /**
     * 平均答題時間（毫秒）
     * 用於速度獎勵計算
     */
    @Column(name = "average_response_time_ms")
    private Long averageResponseTimeMs;

    // ==================== 智能推薦欄位（v2.0）====================

    /**
     * 下次建議複習時間（基於遺忘曲線預測）
     * 用於主動推送提醒
     */
    @Column(name = "next_recommended_review_time")
    private LocalDateTime nextRecommendedReviewTime;

    // ==================== 業務邏輯方法 ====================

    /**
     * 判斷是否為遺忘狀態（S-1）
     *
     * @return true 如果 current_state = 'S-1'
     */
    public boolean isForgotten() {
        return "S-1".equals(this.currentState);
    }

    /**
     * 判斷是否為真正的新單字（從未閱讀過）
     *
     * @return true 如果從未學習過
     */
    public boolean isNewWord() {
        return "S0".equals(this.currentState) && !this.hasEverLearned;
    }

    /**
     * 標記為首次學習（只會在閱讀時觸發）
     *
     * 業務規則：
     * - 所有學習都從閱讀開始
     * - 閱讀 ≥ 5 秒後自動調用
     * - 冪等操作：可重複調用
     */
    public void markAsFirstLearn() {
        if (!this.hasEverLearned) {
            this.hasEverLearned = true;
            this.firstLearnTime = LocalDateTime.now();
        }
    }

    /**
     * 標記為已遺忘
     *
     * 執行效果：
     * - forgotten_count +1
     * - 更新 last_forgotten_time
     * - 重置 consecutive_correct = 0
     *
     * @return 當前遺忘次數
     */
    public int markAsForgotten() {
        this.forgottenCount++;
        this.lastForgottenTime = LocalDateTime.now();
        this.consecutiveCorrect = 0;
        return this.forgottenCount;
    }

    /**
     * 記錄閱讀行為（被動學習）
     *
     * 執行內容：
     * 1. 更新閱讀統計
     * 2. 如果閱讀 ≥ 5 秒，標記為已學習
     *
     * 注意：本方法不直接修改 memory_strength
     *       記憶強度由 AlgorithmCoreService 統一計算
     *
     * @param readDurationSeconds 本次閱讀時長（秒）
     * @return 是否達到有效閱讀標準（≥ 5 秒）
     */
    public boolean recordReading(double readDurationSeconds) {
        this.readCount++;
        this.totalReadDuration += readDurationSeconds;
        this.avgReadDuration = this.totalReadDuration / this.readCount;
        this.lastReadTime = LocalDateTime.now();

        // 有效閱讀判定
        boolean isEffective = readDurationSeconds >= 5.0;

        // 首次有效閱讀 = 首次學習
        if (isEffective && !this.hasEverLearned) {
            markAsFirstLearn();
        }

        return isEffective;
    }

    /**
     * 記錄答題結果（主動學習）
     *
     * 前置條件：使用者已經閱讀過此單字（has_ever_learned = true）
     *
     * 執行內容：
     * 1. 更新答題統計
     * 2. 更新連續答對計數
     * 3. 更新平均答題時間（移動平均）
     * 4. 複習次數 +1
     *
     * @param isCorrect 是否答對
     * @param responseTimeMs 答題時間（毫秒）
     */
    public void recordAnswer(boolean isCorrect, long responseTimeMs) {
        this.totalReviewCount++;

        if (isCorrect) {
            this.totalCorrect++;
            this.consecutiveCorrect++;
        } else {
            this.totalIncorrect++;
            this.consecutiveCorrect = 0;
        }

        // 移動平均計算
        if (this.averageResponseTimeMs == null) {
            this.averageResponseTimeMs = responseTimeMs;
        } else {
            long oldAvg = this.averageResponseTimeMs;
            int n = this.totalReviewCount;
            this.averageResponseTimeMs = (oldAvg * (n - 1) + responseTimeMs) / n;
        }
    }

    /**
     * 計算答對率
     *
     * @return 答對率 [0.0, 1.0]，從未答題時返回 0.0
     */
    public double getAccuracyRate() {
        int total = this.totalCorrect + this.totalIncorrect;
        if (total == 0) return 0.0;
        return (double) this.totalCorrect / total;
    }
}