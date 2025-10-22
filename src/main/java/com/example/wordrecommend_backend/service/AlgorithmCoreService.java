package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.config.AlgorithmConfig;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 演算法核心服務 (純計算器)
 *
 * 這個服務不進行任何資料庫操作，只負責執行演算法的數學模型。
 * 它的所有方法都是無狀態的，給予相同的輸入，永遠會得到相同的輸出。
 */
@Service
@RequiredArgsConstructor
public class AlgorithmCoreService {

    private final AlgorithmConfig config;

    /**
     * 主計算方法：計算新的記憶強度
     * M_i(新) = M_i(衰減後) + f(回饋增益)
     */
    public double calculateNewMemoryStrength(WordState state, Word word, boolean isCorrect, long durationMs, LocalDateTime currentTime) {
        // 1. 先計算時間造成的記憶衰減
        double decayedStrength = calculateDecay(state, word, currentTime);

        // 2. 再計算本次答題帶來的回饋增益
        double feedbackGain = calculateFeedbackGain(state, isCorrect, durationMs);

        // 3. 將兩者相加，並確保結果在 [0, 1] 範圍內
        double rawNewStrength = decayedStrength + feedbackGain;
        return Math.min(1.0, Math.max(0.0, rawNewStrength));
    }

    /**
     * 1. 計算時間衰減
     * 公式：M_i(衰減後) = M_i(上次) * e^(-λ' * Δt)
     * 其中 λ' (動態遺忘速率) 會受到單字複雜度影響
     */
    public double calculateDecay(WordState state, Word word, LocalDateTime currentTime) {
        double lastStrength = state.getMemoryStrength();
        LocalDateTime lastReviewTime = state.getLastReviewTime();
        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);

        // 【演算法深化】整合單字複雜度，計算動態遺忘速率 (λ')
        // 基礎遺忘率 * (1 + 複雜度分數)，讓難的單字忘得更快
        double dynamicLambda = config.getLambdaDecay() * (1 + word.getComplexityScore());

        return lastStrength * Math.exp(-dynamicLambda * deltaT);
    }

    /**
     * 2. 計算回饋增益 (f)
     * 採用我們討論過的「方案三」：獎勵與狀態掛鉤，更符合認知科學
     */
    public double calculateFeedbackGain(WordState state, boolean isCorrect, long durationMs) {
        String previousState = state.getCurrentState();
        double gain = 0.0;

        // 根據答對與否以及當前狀態，給予不同的基礎獎勵/懲罰
        if (isCorrect) {
            switch (previousState) {
                case "S0": gain += config.getAlphaRewardS0(); break;
                case "S1": gain += config.getAlphaRewardS1(); break;
                case "S2": gain += config.getAlphaRewardS2(); break;
                case "S3": gain += config.getAlphaRewardS3(); break;
                default: gain += config.getAlphaReward(); break; // 預設值
            }
        } else {
            // 懲罰也可以根據狀態設計，目前先使用統一懲罰
            gain -= config.getBetaPenalty();
        }

        // 加上速度獎勵項
        double tMax = config.getTMaxMs();
        double effectiveTime = Math.min(durationMs, tMax);
        double speedFactor = 1.0 - (effectiveTime / tMax);
        gain += config.getGammaSpeed() * speedFactor;

        return gain;
    }

    /**
     * 輔助方法：根據新的記憶強度，決定 FSM 狀態
     */
    public String determineFsmState(double strength) {
        if (strength >= config.getThresholdS3()) {
            return "S3"; // 穩定記憶
        } else if (strength >= config.getThresholdS2()) {
            return "S2"; // 熟悉
        } else if (strength > 0) {
            return "S1"; // 不熟悉
        } else {
            return "S0"; // 未學習/完全遺忘
        }
    }

    /**
     * 輔助方法：計算推薦優先度 Pᵢ
     * 公式：Pᵢ = w₁ * (1 - Mᵢ) + w₂ * e^(λΔt)
     */
    public double calculateReviewPriority(double newStrength, LocalDateTime lastReviewTime, LocalDateTime currentTime, double complexityScore) {
        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);

        // 【演算法深化】這裡的遺忘率也應該使用考慮了複雜度的動態值
        double dynamicLambda = config.getLambdaDecay() * (1 + complexityScore);
        double urgencyFactor = Math.exp(dynamicLambda * deltaT);

        // 優先度 = (1 - 記憶強度) * 權重1 + 急迫性 * 權重2
        return config.getW1Strength() * (1.0 - newStrength) + config.getW2Urgency() * urgencyFactor;
    }
}