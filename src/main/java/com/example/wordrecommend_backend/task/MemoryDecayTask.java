package com.example.wordrecommend_backend.task;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.repository.UserRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import com.example.wordrecommend_backend.service.AlgorithmCoreService;
import com.example.wordrecommend_backend.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 記憶衰減定時任務（Phase 7）
 *
 * 責任：
 * - 每天定時執行（默認 00:00:00）
 * - 遍歷所有使用者的 WordState
 * - 根據 FSM 狀態應用不同的衰減幅度
 * - 自動更新 memory_strength 和 current_state
 *
 * 衰減策略：
 * - S0（新單字）：不衰減（未開始學習）
 * - S1（學習中）：衰減幅度大（0.15/天）
 * - S2（複習中）：衰減幅度中（0.08/天）
 * - S3（已精通）：衰減幅度小（0.03/天）
 * - S-1（已遺忘）：不衰減（已標記遺忘，等待複習）
 *
 * @author kimonos-test
 * @version 1.0
 * @since Phase 7
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryDecayTask {

    private final UserRepository userRepository;
    private final WordStateRepository wordStateRepository;
    private final AlgorithmCoreService algorithmCoreService;

    // ==================== 定時任務：每天凌晨執行 ====================

    /**
     * 每天凌晨 00:00:00 執行定時衰減
     *
     * Cron 表達式說明：
     * - 秒：0（第 0 秒）
     * - 分：0（第 0 分）
     * - 時：0（凌晨 0 點）
     * - 日：*（每天）
     * - 月：*（每月）
     * - 星期：?（不指定）
     *
     * 時區：根據 application.properties 中的 spring.jpa.properties.hibernate.jdbc.time_zone
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void decayAllUserMemories() {

        log.info("🔵 ========== 開始每日記憶衰減任務 ==========");
        log.info("執行時間: {}", LocalDateTime.now());

        long startTime = System.currentTimeMillis();

        try {
            // ========== 步驟 1：獲取所有使用者 ==========
            List<User> allUsers = userRepository.findAll();

            log.info("開始處理 {} 個使用者的記憶衰減", allUsers.size());

            // ========== 步驟 2：為每個使用者執行衰減 ==========
            int totalUpdated = 0;
            int totalErrors = 0;

            for (User user : allUsers) {
                try {
                    int updated = decayUserMemories(user);
                    totalUpdated += updated;

                    log.debug("使用者 {} 完成: 更新了 {} 個 WordState",
                            user.getId(), updated);

                } catch (Exception e) {
                    totalErrors++;
                    log.error("❌ 使用者 {} 的衰減任務失敗: {}",
                            user.getId(), e.getMessage(), e);
                }
            }

            // ========== 步驟 3：日誌總結 ==========
            long duration = System.currentTimeMillis() - startTime;

            log.info("✅ 每日記憶衰減任務完成!");
            log.info("總計: {} 個 WordState 已更新", totalUpdated);
            log.info("錯誤: {} 個使用者處理失敗", totalErrors);
            log.info("執行耗時: {} ms", duration);
            log.info("🔵 ========== 結束每日記憶衰減任務 ==========");

        } catch (Exception e) {
            log.error("❌ 每日記憶衰減任務發生嚴重錯誤!", e);
        }
    }

    // ==================== 核心邏輯：為使用者衰減記憶 ====================

    /**
     * 為單個使用者的所有 WordState 應用衰減
     *
     * @param user 目標使用者
     * @return 更新的 WordState 數量
     */
    private int decayUserMemories(User user) {

        log.debug("🟡 開始衰減使用者 {} 的記憶", user.getId());

        // 獲取該使用者的所有 WordState
        List<WordState> allWordStates = wordStateRepository.findByUser(user);

        log.trace("使用者 {} 擁有 {} 個 WordState", user.getId(), allWordStates.size());

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;

        for (WordState state : allWordStates) {
            try {
                boolean changed = applyDecayToWordState(state, now);

                if (changed) {
                    updated++;

                    // 保存更新
                    wordStateRepository.save(state);

                    log.trace("已衰減: word='{}', strength: {:.3f} → {:.3f}, state: {} → {}",
                            state.getWord().getWordText(),
                            state.getMemoryStrength() == null ? 0 : state.getMemoryStrength(),
                            state.getMemoryStrength(),
                            state.getCurrentState(),
                            state.getCurrentState());
                }

            } catch (Exception e) {
                log.error("❌ 衰減失敗: user={}, word='{}': {}",
                        user.getId(), state.getWord().getWordText(), e.getMessage(), e);
            }
        }

        log.debug("✅ 使用者 {} 衰減完成: {} 個已更新", user.getId(), updated);

        return updated;
    }

    // ==================== 核心邏輯：應用衰減到單個 WordState ====================

    /**
     * 為單個 WordState 應用衰減
     *
     * 衰減策略（基於 FSM 狀態）：
     *
     * 🔑 S0（新單字）：不衰減
     *    理由：未開始學習，無需衰減
     *    新強度 = 原強度（通常為 0.0）
     *
     * 🔑 S1（學習中）：衰減幅度大（0.15/天）
     *    理由：記憶不穩定，容易遺忘
     *    新強度 = 原強度 - 0.15
     *
     * 🔑 S2（複習中）：衰減幅度中（0.08/天）
     *    理由：記憶逐漸穩定，衰減減少
     *    新強度 = 原強度 - 0.08
     *
     * 🔑 S3（已精通）：衰減幅度小（0.03/天）
     *    理由：記憶非常穩定，衰減最小
     *    新強度 = 原強度 - 0.03
     *
     * 🔑 S-1（已遺忘）：不衰減
     *    理由：已標記遺忘，等待複習，不自動衰減
     *    新強度 = 原強度（通常為 0.0）
     *
     * 限制：新強度必須在 [0.0, 1.0] 範圍內
     *
     * @param state 目標 WordState
     * @param currentTime 當前時間
     * @return 是否有更新
     */
    private boolean applyDecayToWordState(WordState state, LocalDateTime currentTime) {

        String currentStateStr = state.getCurrentState();
        Double oldStrength = state.getMemoryStrength();

        if (oldStrength == null) {
            oldStrength = 0.0;
            state.setMemoryStrength(oldStrength);
        }

        // ========== 決定衰減幅度 ==========
        Double decayAmount = getDecayAmount(currentStateStr);

        log.trace("衰減參數: state={}, old_strength={:.3f}, decay_amount={:.3f}",
                currentStateStr, oldStrength, decayAmount);

        // ========== 計算新強度 ==========
        Double newStrength = oldStrength - decayAmount;

        // 限制在 [0.0, 1.0] 範圍內
        newStrength = Math.max(0.0, Math.min(1.0, newStrength));

        // 如果沒有變化，返回 false（不需更新）
        if (Math.abs(newStrength - oldStrength) < 0.0001) {
            log.trace("沒有實質變化，跳過");
            return false;
        }

        // ========== 更新強度 ==========
        state.setMemoryStrength(newStrength);

        // ========== 判定新狀態 ==========
        String newStateStr = algorithmCoreService.determineFsmState(state);

        // 如果狀態有變化，記錄
        if (!currentStateStr.equals(newStateStr)) {
            log.debug("🔴 狀態變化: {} → {} (strength: {:.3f} → {:.3f})",
                    currentStateStr, newStateStr, oldStrength, newStrength);

            state.setCurrentState(newStateStr);
        }

        // ========== 更新最後衰減時間 ==========
        state.setLastReviewTime(currentTime);

        return true;
    }

    // ==================== 輔助方法：根據狀態決定衰減幅度 ====================

    /**
     * 根據 FSM 狀態返回衰減幅度
     *
     * 衰減幅度表（基於經驗值）：
     *
     * | 狀態 | 描述 | 衰減幅度 | 理由 |
     * |------|------|--------|------|
     * | S0 | 新單字 | 0.00 | 未開始學習 |
     * | S1 | 學習中 | 0.15 | 記憶不穩定 |
     * | S2 | 複習中 | 0.08 | 記憶逐漸穩定 |
     * | S3 | 已精通 | 0.03 | 記憶非常穩定 |
     * | S-1 | 已遺忘 | 0.00 | 等待複習 |
     *
     * 說明：
     * - 衰減幅度越大，記憶越容易遺忘
     * - 衰減幅度越小，記憶越穩定
     * - 應用場景：每天自動衰減一次
     *
     * @param state 當前 FSM 狀態（"S0", "S1", "S2", "S3", "S-1"）
     * @return 衰減幅度（0.0-0.15）
     */
    private Double getDecayAmount(String state) {

        switch (state) {
            case "S0":   // 新單字：不衰減
                log.trace("衰減幅度決定: S0 → 0.00（未開始學習）");
                return 0.0;

            case "S1":   // 學習中：衰減幅度大（記憶不穩定）
                log.trace("衰減幅度決定: S1 → 0.15（記憶不穩定）");
                return 0.15;

            case "S2":   // 複習中：衰減幅度中（記憶逐漸穩定）
                log.trace("衰減幅度決定: S2 → 0.08（記憶逐漸穩定）");
                return 0.08;

            case "S3":   // 已精通：衰減幅度小（記憶非常穩定）
                log.trace("衰減幅度決定: S3 → 0.03（記憶非常穩定）");
                return 0.03;

            case "S-1":  // 已遺忘：不衰減（等待複習）
                log.trace("衰減幅度決定: S-1 → 0.00（等待複習）");
                return 0.0;

            default:
                log.warn("⚠️ 未知的 FSM 狀態: {}, 使用預設衰減 0.0", state);
                return 0.0;
        }
    }

    // ==================== 測試用方法：手動觸發衰減 ====================

    /**
     * 手動觸發衰減（測試用）
     *
     * 🔑 只在開發測試時使用，生產環境不應暴露此方法
     *
     * @return 更新的 WordState 總數
     */
    @Transactional
    public int manualTriggerDecay() {

        log.warn("🟡 手動觸發記憶衰減（測試用）");

        List<User> allUsers = userRepository.findAll();

        int totalUpdated = 0;

        for (User user : allUsers) {
            totalUpdated += decayUserMemories(user);
        }

        log.info("✅ 手動衰減完成: 更新了 {} 個 WordState", totalUpdated);

        return totalUpdated;
    }
}