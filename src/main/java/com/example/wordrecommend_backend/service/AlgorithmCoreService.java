package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.config.AlgorithmConfig;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 演算法核心服務 (純計算器)
 *
 * 這個服務不進行任何資料庫操作，只負責執行演算法的數學模型。
 * 它的所有方法都是無狀態的，給予相同的輸入，永遠會得到相同的輸出。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlgorithmCoreService {

    private final AlgorithmConfig config;

    public double calculateDecay(WordState state, Word word, LocalDateTime currentTime) {
        // ========== 1. 獲取上次記憶強度 ==========
        double lastStrength = state.getMemoryStrength();

        // ========== 2. 計算時間間隔（天數）==========
        LocalDateTime lastReviewTime = state.getLastReviewTime();
        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);

        // ========== 3. 計算個人化動態遺忘率 ==========
        double dynamicLambda = calculatePersonalizedDecayRate(state, word);

        // ========== 4. 指數衰減公式 ==========
        // Math.exp(x) = e^x
        double decayFactor = Math.exp(-dynamicLambda * deltaT);
        double decayedStrength = lastStrength * decayFactor;

        // ========== 5. 詳細日誌 ==========
        log.trace("Decay for word '{}': λ'={:.4f}, Δt={:.2f} days, " +
                        "M_last={:.3f}, decay_factor={:.3f}, M_decay={:.3f}",
                word.getWordText(), dynamicLambda, deltaT,
                lastStrength, decayFactor, decayedStrength);

        return decayedStrength;
    }

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

//    /**
//     * 1. 計算時間衰減
//     * 公式：M_i(衰減後) = M_i(上次) * e^(-λ' * Δt)
//     * 其中 λ' (動態遺忘速率) 會受到單字複雜度影響
//     */
//    public double calculateDecay(WordState state, Word word, LocalDateTime currentTime) {
//        double lastStrength = state.getMemoryStrength();
//        LocalDateTime lastReviewTime = state.getLastReviewTime();
//        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);
//
//        // 【演算法深化】整合單字複雜度，計算動態遺忘速率 (λ')
//        // 基礎遺忘率 * (1 + 複雜度分數)，讓難的單字忘得更快
//        double dynamicLambda = config.getLambdaDecay() * (1 + word.getComplexityScore());
//
//        return lastStrength * Math.exp(-dynamicLambda * deltaT);
//    }
//
//    /**
//     * 2. 計算回饋增益 (f)
//     * 採用我們討論過的「方案三」：獎勵與狀態掛鉤，更符合認知科學
//     */
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
    public String determineFsmState(double strength, boolean hasEverLearned) {
        // 確保 strength 在有效範圍內
        strength = clamp(strength, 0.0, 1.0);

        if (strength >= config.getThresholdS3()) {
            return "S3"; // 精通
        } else if (strength >= config.getThresholdS2()) {
            return "S2"; // 熟悉
        } else if (strength > 0.0) {
            return "S1"; // 初學/不熟
        } else {
            // 🔑 關鍵區分點：記憶強度為 0 時
            if (hasEverLearned) {
                log.debug("Word identified as S-1 (forgotten): strength=0, has_ever_learned=true");
                return "S-1"; // 遺忘
            } else {
                log.debug("Word identified as S0 (new): strength=0, has_ever_learned=false");
                return "S0"; // 新單字
            }
        }
    }
    public String determineFsmState(WordState state) {
        return determineFsmState(state.getMemoryStrength(), state.getHasEverLearned());
    }
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double calculatePersonalizedDecayRate(WordState state, Word word) {
        // 1. 基礎遺忘率（從配置讀取）
        double lambdaBase = config.getLambdaDecay(); // 預設 0.1

        // ========== 2. 複雜度因子（全局屬性）==========
        // 理論：困難單字確實更容易遺忘（認知負荷理論）
        //
        // 範圍計算：
        // - complexity = 0.0 → factor = 1.0（簡單單字）
        // - complexity = 0.5 → factor = 1.5（中等難度）
        // - complexity = 1.0 → factor = 2.0（最難單字）
        double complexityScore = word.getComplexityScore();
        double complexityFactor = 1.0 + complexityScore;

        // ========== 3. 遺忘歷史因子（個人化）==========
        // 理論：反覆遺忘的單字，記憶痕跡不穩定
        //
        // 使用對數避免過度懲罰：
        // - forgotten = 0 → factor = 1.00（無遺忘歷史）
        // - forgotten = 1 → factor = 1.07（增加 7%）
        // - forgotten = 3 → factor = 1.14（增加 14%）
        // - forgotten = 5 → factor = 1.18（增加 18%）
        // - forgotten = 10 → factor = 1.24（增加 24%）
        int forgottenCount = state.getForgottenCount();
        double forgottenFactor = 1.0;

        if (forgottenCount > 0) {
            // Math.log1p(x) = ln(1 + x)，數值更穩定
            double logForgotten = Math.log1p(forgottenCount);
            forgottenFactor = 1.0 + logForgotten * config.getKForgotten(); // k = 0.1
        }

        // ========== 4. 綜合計算 ==========
        double personalizedLambda = lambdaBase * complexityFactor * forgottenFactor;

        // ========== 5. 詳細日誌（用於論文分析）==========
        log.trace("Personalized λ' for word '{}' (user {}): " +
                        "base={:.3f}, complexity={:.2f} (factor={:.2f}), " +
                        "forgotten={} (factor={:.2f}), final={:.4f}",
                word.getWordText(),
                state.getUser().getId(),
                lambdaBase,
                complexityScore, complexityFactor,
                forgottenCount, forgottenFactor,
                personalizedLambda);

        return personalizedLambda;
    }
    public double calculateReadingGain(double readDurationSeconds, int currentReadCount) {
        // ========== 1. 無效閱讀過濾 ==========
        // 閱讀時長低於閾值視為無效（過濾快速滾動）
        if (readDurationSeconds < config.getMinEffectiveReadingSeconds()) {
            log.trace("Reading too short ({:.1f}s < {:.1f}s), no gain",
                    readDurationSeconds, config.getMinEffectiveReadingSeconds());
            return 0.0;
        }

        // ========== 2. 基礎獎勵 ==========
        double alphaRead = config.getAlphaReading(); // 0.05

        // ========== 3. 時長因子 ==========
        // 理論：閱讀越久，理解越深
        // 但達到最佳時長後不再增加（避免過度閱讀）
        double tOptimal = config.getOptimalReadingSeconds(); // 30.0
        double durationFactor = Math.min(readDurationSeconds / tOptimal, 1.0);

        // ========== 4. 次數衰減因子 ==========
        // 理論：邊際效益遞減（第 1 次閱讀 vs 第 10 次閱讀）
        // 使用對數避免懲罰過度
        //
        // 計算說明：
        // - Math.log1p(x) = ln(1 + x)，數值更穩定
        // - 加 1 是因為 log(1) = 0（首次閱讀不應衰減）
        double logReadCount = Math.log1p(currentReadCount);
        double diminishingFactor = 1.0 / (1.0 + logReadCount * config.getKDiminishing());

        // ========== 5. 綜合計算 ==========
        double gain = alphaRead * durationFactor * diminishingFactor;

        // ========== 6. 詳細日誌（用於論文分析）==========
        log.trace("Reading gain: duration={:.1f}s (factor={:.2f}), " +
                        "count={} (diminish={:.2f}), gain={:.4f}",
                readDurationSeconds, durationFactor,
                currentReadCount, diminishingFactor,
                gain);

        return gain;
    }
    public double calculateNewMemoryStrengthFromReading(
            WordState state,
            Word word,
            double readDurationSeconds,
            LocalDateTime currentTime) {

        // ========== 1. 計算時間衰減 ==========
        // 使用個人化動態遺忘率
        double decayedStrength = calculateDecay(state, word, currentTime);

        // ========== 2. 計算閱讀增益 ==========
        double readingGain = calculateReadingGain(readDurationSeconds, state.getReadCount());

        // ========== 3. 綜合計算 ==========
        double rawNewStrength = decayedStrength + readingGain;

        // ========== 4. 限制在 [0, 1] 範圍內 ==========
        double clampedStrength = clamp(rawNewStrength, 0.0, 1.0);

        // ========== 5. 詳細日誌 ==========
        log.debug("Reading update for word '{}': " +
                        "decayed={:.3f}, reading_gain={:.3f}, new={:.3f}",
                word.getWordText(), decayedStrength, readingGain, clampedStrength);

        return clampedStrength;
    }
    public double calculateReviewPriority(
            WordState state,
            Word word,
            LocalDateTime currentTime) {

        double strength = state.getMemoryStrength();
        LocalDateTime lastReviewTime = state.getLastReviewTime();

        // ========== 1. 記憶強度項 ==========
        // 理論：記憶越弱，越需要複習
        // strength = 0 → component = 1.0（最需要）
        // strength = 1 → component = 0.0（不需要）
        double strengthComponent = config.getW1Strength() * (1.0 - strength);

        // ========== 2. 時間急迫性項 ==========
        // 理論：距離上次複習越久，越需要複習
        // 使用個人化動態遺忘率（考慮單字難度和個人表現）
        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);
        double dynamicLambda = calculatePersonalizedDecayRate(state, word);

        // 指數增長：時間越久，優先度增長越快
        double urgencyComponent = config.getW2Urgency() * Math.exp(dynamicLambda * deltaT);

        // ========== 3. S-1 狀態特殊加成（核心創新）==========
        double forgottenBonus = 0.0;

        if (state.isForgotten()) {
            // 計算距離遺忘多久了
            LocalDateTime lastForgottenTime = state.getLastForgottenTime();

            if (lastForgottenTime != null) {
                long daysSinceForgotten = TimeUtil.calculateDaysDifferenceAsLong(
                        lastForgottenTime, currentTime
                );

                // 時間相關加成（剛遺忘 vs 久遠遺忘）
                double baseBonus = config.getKS1Bonus(); // 50.0

                if (daysSinceForgotten <= 3) {
                    // 剛遺忘（3 天內）：高優先度
                    // 理由：記憶痕跡還在，容易恢復
                    forgottenBonus = baseBonus;
                    log.debug("S-1 bonus (recent, {} days): {}", daysSinceForgotten, forgottenBonus);

                } else if (daysSinceForgotten <= 7) {
                    // 中期遺忘（7 天內）：中優先度
                    forgottenBonus = baseBonus * 0.6;
                    log.debug("S-1 bonus (medium, {} days): {}", daysSinceForgotten, forgottenBonus);

                } else {
                    // 長期遺忘（7 天以上）：低優先度
                    // 理由：已徹底忘記，和新單字差不多
                    forgottenBonus = baseBonus * 0.3;
                    log.debug("S-1 bonus (old, {} days): {}", daysSinceForgotten, forgottenBonus);
                }

                // 遺忘次數折扣（反覆遺忘的單字，降低期望）
                // forgotten_count = 0 → factor = 1.0
                // forgotten_count = 3 → factor = 0.25
                int forgottenCount = state.getForgottenCount();
                if (forgottenCount > 1) {
                    double discountFactor = 1.0 / (1.0 + forgottenCount * 0.5);
                    forgottenBonus *= discountFactor;
                    log.debug("S-1 bonus after forgotten_count discount ({}x): {}",
                            forgottenCount, forgottenBonus);
                }
            } else {
                // 如果沒有 last_forgotten_time（資料異常），給予基礎加成
                forgottenBonus = config.getKS1Bonus() * 0.5;
                log.warn("S-1 state but no last_forgotten_time, using default bonus: {}",
                        forgottenBonus);
            }
        }

        // ========== 4. 綜合計算 ==========
        double totalPriority = strengthComponent + urgencyComponent + forgottenBonus;

        // ========== 5. 詳細日誌（用於論文分析）==========
        log.debug("Priority for word '{}' (state={}, M={:.2f}): " +
                        "strength_component={:.2f}, urgency_component={:.2f} (λ'={:.3f}, Δt={:.1f}), " +
                        "forgotten_bonus={:.2f}, total={:.2f}",
                word.getWordText(), state.getCurrentState(), strength,
                strengthComponent, urgencyComponent, dynamicLambda, deltaT,
                forgottenBonus, totalPriority);

        return totalPriority;
    }

//    /**
//     * 輔助方法：計算推薦優先度 Pᵢ
//     * 公式：Pᵢ = w₁ * (1 - Mᵢ) + w₂ * e^(λΔt)
//     */
//    public double calculateReviewPriority(double newStrength, LocalDateTime lastReviewTime, LocalDateTime currentTime, double complexityScore) {
//        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);
//
//        // 【演算法深化】這裡的遺忘率也應該使用考慮了複雜度的動態值
//        double dynamicLambda = config.getLambdaDecay() * (1 + complexityScore);
//        double urgencyFactor = Math.exp(dynamicLambda * deltaT);
//
//        // 優先度 = (1 - 記憶強度) * 權重1 + 急迫性 * 權重2
//        return config.getW1Strength() * (1.0 - newStrength) + config.getW2Urgency() * urgencyFactor;
//    }
}