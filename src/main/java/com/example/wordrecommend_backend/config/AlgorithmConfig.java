package com.example.wordrecommend_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 讀取 application.yml/properties 中 learning.algorithm 前綴的所有演算法參數
 * (已更新為支援「方案三」的版本)
 */
@Component
@ConfigurationProperties(prefix = "learning.algorithm")
@Data
public class AlgorithmConfig {

    // --- 基礎參數 ---
    // 衰減係數 (固定 λ)
    private double lambdaDecay;

    // 統一的答錯懲罰
    private double betaPenalty;

    // 速度獎勵權重
    private double gammaSpeed;

    // 時間上限 (毫秒)
    private long tMaxMs;

    // --- 推薦優先度 P_i 的權重 ---
    private double w1Strength;
    private double w2Urgency;

    // --- 狀態切換閾值 ---
    private double thresholdS1;
    private double thresholdS2;
    private double thresholdS3;

    // --- 【核心更新】分狀態的答對獎勵 (Alpha) ---
    private double alphaReward;   // 保留作為預設值
    private double alphaRewardS0; // 首次學習
    private double alphaRewardS1; // 從不熟悉到熟悉
    private double alphaRewardS2; // 鞏固熟悉
    private double alphaRewardS3; // 維持穩定

    // ==================== v2.0 新增參數 ====================

    /**
     * 閱讀基礎獎勵 α_read
     *
     * 設計理念：
     * - 閱讀是被動學習，效果比答題低
     * - 預設值：0.05（答題的 1/4）
     * - 閱讀一次最多提升 5%
     *
     * 論文依據：
     * - 被動學習（閱讀）vs 主動回憶（答題）
     * - 認知負荷理論：主動回憶效果更好
     */
    private Double alphaReading = 0.05;

    /**
     * 有效閱讀最小時長（秒）
     *
     * 低於此值視為無效閱讀（過濾快速滾動）
     * 預設值：5.0 秒
     *
     * 理由：
     * - 人類閱讀理解需要時間
     * - 過濾「刷閱讀次數」的作弊行為
     */
    private Double minEffectiveReadingSeconds = 5.0;

    /**
     * 最佳閱讀時長（秒）
     *
     * 達到此時長後，增益不再增加
     * 預設值：30.0 秒
     *
     * 理由：
     * - 避免過度閱讀同一單字（邊際效益遞減）
     * - 30 秒足夠理解單字的釋義、例句
     */
    private Double optimalReadingSeconds = 30.0;

    /**
     * 閱讀次數衰減係數
     *
     * 用於計算：diminishing_factor = 1 / (1 + log(read_count) × k_diminish)
     * 預設值：0.3
     *
     * 效果：
     * - 第 1 次：factor = 1.0
     * - 第 2 次：factor = 0.82
     * - 第 5 次：factor = 0.62
     * - 第 10 次：factor = 0.51
     */
    private Double kDiminishing = 0.3;

    /**
     * 遺忘歷史懲罰係數
     *
     * 用於計算：forgotten_factor = 1.0 + log1p(forgotten_count) × k_forgotten
     * 預設值：0.1
     *
     * 效果：
     * - forgotten = 0 → factor = 1.0
     * - forgotten = 1 → factor = 1.07
     * - forgotten = 3 → factor = 1.14
     * - forgotten = 5 → factor = 1.18
     *
     * 論文依據：
     * - 反覆遺忘的單字，記憶痕跡不穩定
     * - 遺忘曲線更陡峭（Ebbinghaus 遺忘曲線理論）
     */
    private Double kForgotten = 0.1;

    /**
     * S-1 狀態遺忘加成基數（時間相關）
     *
     * 用於計算：
     * - days <= 3: bonus = k_s1_bonus
     * - days <= 7: bonus = k_s1_bonus × 0.6
     * - days > 7:  bonus = k_s1_bonus × 0.3
     *
     * 預設值：50.0
     *
     * 設計理念：
     * - 剛遺忘（3 天內）：高優先度（還有印象）
     * - 中期遺忘（7 天內）：中優先度
     * - 長期遺忘（7 天外）：低優先度（當新單字）
     */
    private Double kS1Bonus = 50.0;
}