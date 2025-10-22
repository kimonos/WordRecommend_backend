//package com.example.wordrecommend_backend.service;
//
//import com.example.wordrecommend_backend.config.AlgorithmConfig;
//import com.example.wordrecommend_backend.entity.ReviewHistory;
//import com.example.wordrecommend_backend.entity.Word;
//import com.example.wordrecommend_backend.entity.WordState;
//import com.example.wordrecommend_backend.repository.ReviewHistoryRepository;
//import com.example.wordrecommend_backend.repository.WordStateRepository;
//import com.example.wordrecommend_backend.util.TimeUtil;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.Optional;
//
//@Service
//public class StateUpdateService {
//
//    private final WordStateRepository wordStateRepository;
//    private final ReviewHistoryRepository reviewHistoryRepository;
//    private final AlgorithmConfig config;
//
//    public StateUpdateService(WordStateRepository wordStateRepository, ReviewHistoryRepository reviewHistoryRepository, AlgorithmConfig config) {
//        this.wordStateRepository = wordStateRepository;
//        this.reviewHistoryRepository = reviewHistoryRepository;
//        this.config = config;
//    }
//
//    @Transactional
//    public WordState processAnswerFeedback(Long wordId, boolean isCorrect, long answerTimeMs) {
//        Word word = new Word();
//        word.setId(wordId);
//
//        Optional<WordState> optionalState = wordStateRepository.findByWord(word);
//        WordState currentState = optionalState.orElseGet(() -> initializeNewState(word));
//
//        LocalDateTime now = LocalDateTime.now();
//
//        // --- 核心計算：傳入整個 currentState 物件 ---
//        double newMemoryStrength = calculateNewMemoryStrength(currentState, isCorrect, answerTimeMs, now);
//
//        // --- 更新狀態實體 ---
//        currentState.setMemoryStrength(newMemoryStrength);
//        currentState.setLastReviewTime(now);
//        currentState.setCurrentState(determineFsmState(newMemoryStrength));
//        // 注意：這裡的 getLastReviewTime() 應該是舊的時間，但為了簡化，我們先用 now
//        // 在更精確的實作中，應該在計算前就保存 lastReviewTime
//        currentState.setNextReviewPriority(calculateReviewPriority(newMemoryStrength, currentState.getLastReviewTime(), now));
//
//        WordState updatedState = wordStateRepository.save(currentState);
//        saveReviewHistory(word, isCorrect, answerTimeMs, now);
//
//        return updatedState;
//    }
//
//    // *** 變更點 3：移除不再需要的測試方法 ***
//    // public int plus(int a, int b) { ... }
//
//    // ===================================
//    // 輔助方法：核心演算法邏輯
//    // ===================================
//
//    /**
//     * 計算新的記憶強度 M_i(新) = M_i(Decayed) + f
//     */
//    private double calculateNewMemoryStrength(WordState state, boolean isCorrect, long answerTimeMs, LocalDateTime currentTime) {
//        double decayedStrength = calculateDecay(state, currentTime);
//        // *** 變更點 2：將整個 state 物件傳遞給 feedbackGain 方法 ***
//        double feedbackGain = calculateFeedbackGain(state, isCorrect, answerTimeMs);
//
//        double rawNewStrength = decayedStrength + feedbackGain;
//        return Math.min(1.0, Math.max(0.0, rawNewStrength));
//    }
//
//    /**
//     * 實作時間衰減公式：M_i(Decayed) = M_i(上次) * e^(-λΔt)
//     */
//    private double calculateDecay(WordState state, LocalDateTime currentTime) {
//        // 未來整合單字複雜度時，會在這裡傳入 word 物件來動態計算 λ
//        double lastStrength = state.getMemoryStrength();
//        LocalDateTime lastReviewTime = state.getLastReviewTime();
//        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);
//        return lastStrength * Math.exp(-config.getLambdaDecay() * deltaT);
//    }
//
//    /**
//     * *** 變更點 1：核心修改 - 實作與狀態掛鉤的回饋函數 (方案三) ***
//     */
//    private double calculateFeedbackGain(WordState state, boolean isCorrect, long answerTimeMs) {
//        String previousState = state.getCurrentState(); // 獲取更新前的狀態
//        double gain = 0.0;
//
//        // 1. 根據狀態決定不同的獎勵 (Alpha) 或懲罰 (Beta)
//        if (isCorrect) {
//            switch (previousState) {
//                case "S0":
//                    gain += config.getAlphaRewardS0(); // 首次學習獎勵
//                    break;
//                case "S1":
//                    gain += config.getAlphaRewardS1(); // 從不熟悉到熟悉，獎勵最高
//                    break;
//                case "S2":
//                    gain += config.getAlphaRewardS2(); // 鞏固熟悉，獎勵次之
//                    break;
//                case "S3":
//                    gain += config.getAlphaRewardS3(); // 維持穩定，獎勵最低
//                    break;
//                default:
//                    // 提供一個預設值以防萬一
//                    gain += config.getAlphaReward();
//                    break;
//            }
//        } else {
//            // 懲罰也可以根據狀態設計，例如在 S3 答錯懲罰更重，這裡我們先用統一懲罰
//            gain -= config.getBetaPenalty();
//        }
//
//        // 2. 速度獎勵項保持不變
//        double tMax = config.getTMaxMs();
//        double effectiveTime = Math.min(answerTimeMs, tMax);
//        double speedFactor = 1.0 - (effectiveTime / tMax);
//        gain += config.getGammaSpeed() * speedFactor;
//
//        return gain;
//    }
//
//    /**
//     * 實作狀態切換邏輯 (FSM)
//     */
//    private String determineFsmState(double strength) {
//        // 為了讓狀態切換更平滑，可以考慮微調閾值
//        if (strength >= config.getThresholdS3()) { // e.g., 0.85
//            return "S3";
//        } else if (strength >= config.getThresholdS2()) { // e.g., 0.5
//            return "S2";
//        } else if (strength > config.getThresholdS1()) { // e.g., 0.10
//            return "S1";
//        } else {
//            return "S0";
//        }
//    }
//
//    /**
//     * 實作推薦優先度 P_i = w1(1 - M_i) + w2 * e^(λΔt)
//     */
//    private double calculateReviewPriority(double strength, LocalDateTime lastReviewTime, LocalDateTime currentTime) {
//        double deltaT = TimeUtil.calculateDaysDifference(lastReviewTime, currentTime);
//        double urgencyFactor = Math.exp(config.getLambdaDecay() * deltaT);
//        return config.getW1Strength() * (1.0 - strength) + config.getW2Urgency() * urgencyFactor;
//    }
//
//    // ===================================
//    // 資料庫存取輔助方法
//    // ===================================
//
//    private WordState initializeNewState(Word word) {
//        WordState newState = new WordState();
//        newState.setWord(word);
//        newState.setMemoryStrength(0.0);
//        newState.setCurrentState("S0");
//        newState.setLastReviewTime(LocalDateTime.now());
//        newState.setNextReviewPriority(100.0);
//        return newState;
//    }
//
//    private void saveReviewHistory(Word word, boolean isCorrect, long answerTimeMs, LocalDateTime reviewTime) {
//        ReviewHistory history = new ReviewHistory();
//        history.setWord(word);
//        history.setIsCorrect(isCorrect);
//        history.setAnswerTimeMs(answerTimeMs);
//        history.setReviewTime(reviewTime);
//        reviewHistoryRepository.save(history);
//    }
//}