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
}