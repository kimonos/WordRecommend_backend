package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.*;
import com.example.wordrecommend_backend.repository.ReviewHistoryRepository;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final AlgorithmCoreService algorithmCoreService;

    // ==================== 公開方法：推薦單字（v2.0 - Phase 5）====================

    /**
     * 核心方法：為使用者推薦單字（v2.0 - 輕量版）
     *
     * 設計理念：
     * - 探索為主（新單字為主）
     * - 學習閉環（適量舊單字）
     * - 遺忘提醒（S-1 單字輕度提醒）
     * - 動態調整（根據新單字剩餘量）
     *
     * @param user 目標使用者
     * @param limit 需要推薦的單字數量
     * @return 推薦的單字列表（包含狀態資訊）
     */
    @Transactional(readOnly = true)
    public List<WordDTO> getWordRecommendations(User user, int limit) {
        if (limit <= 0) return Collections.emptyList();

        LocalDateTime currentTime = LocalDateTime.now();

        // ========== 步驟 1：統計使用者的學習狀態 ==========
        long countS_1 = wordStateRepository.countForgottenWords(user);
        long countS1 = wordStateRepository.countByUserAndState(user, "S1");
        long countS2 = wordStateRepository.countByUserAndState(user, "S2");
        long countS3 = wordStateRepository.countByUserAndState(user, "S3");
        double totalLearned = countS_1 + countS1 + countS2 + countS3;

        log.info("User {} learning stats: S-1={}, S1={}, S2={}, S3={}, total={}",
                user.getId(), countS_1, countS1, countS2, countS3, totalLearned);

        // ========== 步驟 2：根據學習進度和新單字剩餘量決定狀態比例 ==========
        Map<String, Double> stateRatio = new LinkedHashMap<>();

        if (totalLearned < 50) {
            // 新手階段：100% 推薦新單字
            stateRatio.put("S0", 1.0);
            stateRatio.put("S-1", 0.0);
            stateRatio.put("S1", 0.0);
            stateRatio.put("S2", 0.0);
            stateRatio.put("S3", 0.0);

            log.debug("Beginner mode: 100% new words");

        } else {
            // 進階階段：根據新單字剩餘量動態調整

            // 🔑 查詢新單字剩餘數量（精確查詢）
            long availableNewWords = wordRepository.countNewWords(user);

            log.info("User {} has {} new words available (out of total learned: {})",
                    user.getId(), availableNewWords, (long)totalLearned);

            // 🔑 關鍵判斷：新單字是否完全耗盡
            if (availableNewWords == 0) {
                // ========== 情境 D：新單字完全耗盡 - 純複習模式 ==========
                stateRatio.put("S0", 0.0);   // 0% 新單字
                stateRatio.put("S-1", 0.20); // 優先復原遺忘單字
                stateRatio.put("S1", 0.35);  // 複習不熟的
                stateRatio.put("S2", 0.30);  // 複習中等的
                stateRatio.put("S3", 0.15);  // 維持精通的

                log.info("Strategy: Pure Review Mode (no new words available)");

            } else {
                // 還有新單字，根據剩餘比例動態調整
                double newWordRatio = (double)availableNewWords / (totalLearned + availableNewWords);

                log.debug("New word ratio: {:.2f}%", newWordRatio * 100);

                double s1Ratio = (countS_1 > 0) ? 0.05 : 0.0;

                if (newWordRatio > 0.5) {
                    // ========== 情境 A：新單字充足（>50%）- 探索為主 ==========
                    stateRatio.put("S0", 0.60);
                    stateRatio.put("S-1", s1Ratio);
                    stateRatio.put("S1", 0.15);
                    stateRatio.put("S2", 0.15);
                    stateRatio.put("S3", 0.05);

                    log.debug("Strategy: Exploration (60% new words)");

                } else if (newWordRatio > 0.2) {
                    // ========== 情境 B：新單字減少（20-50%）- 平衡模式 ==========
                    stateRatio.put("S0", 0.40);
                    stateRatio.put("S-1", Math.max(s1Ratio, 0.10));
                    stateRatio.put("S1", 0.20);
                    stateRatio.put("S2", 0.20);
                    stateRatio.put("S3", 0.10);

                    log.debug("Strategy: Balanced (40% new words)");

                } else {
                    // ========== 情境 C：新單字稀少（<20%）- 複習為主但保留探索 ==========
                    // 🔑 動態計算新單字比例（確保所有新單字都有機會被學到）
                    double newRatio = Math.max(0.15, Math.min(0.30, newWordRatio * 1.5));

                    stateRatio.put("S0", newRatio);
                    stateRatio.put("S-1", 0.15);
                    stateRatio.put("S1", (1 - newRatio - 0.15) * 0.45);
                    stateRatio.put("S2", (1 - newRatio - 0.15) * 0.40);
                    stateRatio.put("S3", (1 - newRatio - 0.15) * 0.15);

                    log.debug("Strategy: Review-focused ({:.1f}% new words, {} available)",
                            newRatio * 100, availableNewWords);
                }
            }
        }

        // ========== 步驟 3：分配各狀態的配額 ==========
        Map<String, Integer> stateCounts = distributeCounts(limit, stateRatio);
        int numS0 = stateCounts.getOrDefault("S0", 0);
        int numS_1 = stateCounts.getOrDefault("S-1", 0);
        int numS1 = stateCounts.getOrDefault("S1", 0);
        int numS2 = stateCounts.getOrDefault("S2", 0);
        int numS3 = stateCounts.getOrDefault("S3", 0);

        log.debug("Quota allocation: S0={}, S-1={}, S1={}, S2={}, S3={}",
                numS0, numS_1, numS1, numS2, numS3);

        // ========== 步驟 4：動態調整 S0 新單字的難度等級比例 ==========
        double progress = sigmoid(totalLearned, 750.0, 0.02);
        Map<String, Double> levelRatio = new LinkedHashMap<>();
        levelRatio.put("A1", 0.30 - 0.20 * progress);
        levelRatio.put("A2", 0.25 - 0.15 * progress);
        levelRatio.put("B1", 0.20 - 0.05 * progress);
        levelRatio.put("B2", 0.15 - 0.05 * progress);
        levelRatio.put("C1", 0.07 + 0.25 * progress);
        levelRatio.put("C2", 0.03 + 0.20 * progress);

        Map<String, Integer> s0LevelCounts = distributeCounts(numS0, levelRatio);

        // ========== 步驟 5：從資料庫取出各類單字 ==========

        // 5.1 取 S0 新單字（按難度等級分別取，隨機排序）
        List<Word> s0Words = new ArrayList<>();
        for (Map.Entry<String, Integer> e : s0LevelCounts.entrySet()) {
            int take = e.getValue();
            if (take <= 0) continue;
            s0Words.addAll(wordRepository.findNewWordsByLevel(user, e.getKey(), page(take)));
        }

        // 5.2 取 S-1 單字（遺忘單字，輕度優先度排序）
        List<Word> s_1Words = fetchWordsWithPriority(
                user, "S-1", numS_1, currentTime,
                () -> wordStateRepository.findForgottenWords(user, PageRequest.of(0, Math.max(numS_1 * 2, 10)))
        );

        // 5.3 取 S1 單字（輕度優先度排序）
        List<Word> s1Words = fetchWordsWithPriority(
                user, "S1", numS1, currentTime,
                () -> wordStateRepository.findByUserAndState(user, "S1", PageRequest.of(0, Math.max(numS1 * 2, 10)))
        );

        // 5.4 取 S2 單字（輕度優先度排序）
        List<Word> s2Words = fetchWordsWithPriority(
                user, "S2", numS2, currentTime,
                () -> wordStateRepository.findByUserAndState(user, "S2", PageRequest.of(0, Math.max(numS2 * 2, 10)))
        );

        // 5.5 取 S3 單字（隨機即可，已精通）
        List<Word> s3Words = new ArrayList<>();
        if (numS3 > 0) {
            s3Words = wordStateRepository.findByUserAndState(user, "S3", page(numS3))
                    .stream()
                    .map(WordState::getWord)
                    .collect(Collectors.toList());
        }

        // ========== 步驟 6：合併所有單字並去重 ==========
        List<Word> merged = new ArrayList<>(
                s0Words.size() + s_1Words.size() + s1Words.size() + s2Words.size() + s3Words.size()
        );
        merged.addAll(s0Words);
        merged.addAll(s_1Words);
        merged.addAll(s1Words);
        merged.addAll(s2Words);
        merged.addAll(s3Words);

        List<Word> deduped = deduplicateById(merged);

        // ========== 步驟 6.5：智能遞補（如果數量不足）==========
        if (deduped.size() < limit) {
            int missing = limit - deduped.size();
            log.warn("Insufficient words: got {}, need {}, missing {}",
                    deduped.size(), limit, missing);

            // 🔑 遞補策略：優先順序
            // 1. 新單字（如果還有）
            // 2. S-1 遺忘單字
            // 3. S1 不熟的單字
            // 4. S2 複習中的單字
            // 5. S3 精通的單字

            // 嘗試 1：補充新單字
            if (missing > 0) {
                List<Word> extraNewWords = wordRepository.findNewWordsRandomly(user, page(missing * 2));
                for (Word w : extraNewWords) {
                    if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                        deduped.add(w);
                        missing--;
                        if (missing == 0) break;
                    }
                }
                log.debug("After adding new words: {} words, missing {}", deduped.size(), missing);
            }

            // 嘗試 2：補充 S-1 遺忘單字
            if (missing > 0 && countS_1 > 0) {
                List<WordState> extraS_1 = wordStateRepository.findForgottenWords(
                        user, PageRequest.of(0, missing * 2)
                );
                for (WordState ws : extraS_1) {
                    Word w = ws.getWord();
                    if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                        deduped.add(w);
                        missing--;
                        if (missing == 0) break;
                    }
                }
                log.debug("After adding S-1 words: {} words, missing {}", deduped.size(), missing);
            }

            // 嘗試 3：補充 S1 單字
            if (missing > 0 && countS1 > 0) {
                List<WordState> extraS1 = wordStateRepository.findByUserAndState(
                        user, "S1", PageRequest.of(0, missing * 2)
                );
                for (WordState ws : extraS1) {
                    Word w = ws.getWord();
                    if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                        deduped.add(w);
                        missing--;
                        if (missing == 0) break;
                    }
                }
                log.debug("After adding S1 words: {} words, missing {}", deduped.size(), missing);
            }

            // 嘗試 4：補充 S2 單字
            if (missing > 0 && countS2 > 0) {
                List<WordState> extraS2 = wordStateRepository.findByUserAndState(
                        user, "S2", PageRequest.of(0, missing * 2)
                );
                for (WordState ws : extraS2) {
                    Word w = ws.getWord();
                    if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                        deduped.add(w);
                        missing--;
                        if (missing == 0) break;
                    }
                }
                log.debug("After adding S2 words: {} words, missing {}", deduped.size(), missing);
            }

            // 嘗試 5：補充 S3 單字（最後手段）
            if (missing > 0 && countS3 > 0) {
                List<WordState> extraS3 = wordStateRepository.findByUserAndState(
                        user, "S3", PageRequest.of(0, missing * 2)
                );
                for (WordState ws : extraS3) {
                    Word w = ws.getWord();
                    if (deduped.stream().noneMatch(x -> Objects.equals(x.getId(), w.getId()))) {
                        deduped.add(w);
                        missing--;
                        if (missing == 0) break;
                    }
                }
                log.debug("After adding S3 words: {} words, missing {}", deduped.size(), missing);
            }

            if (missing > 0) {
                log.warn("Still missing {} words after all fallback attempts", missing);
            } else {
                log.info("Successfully filled to {} words", deduped.size());
            }
        }

        // 嚴格截斷至 limit
        if (deduped.size() > limit) {
            deduped = new ArrayList<>(deduped.subList(0, limit));
        }

        // ========== 步驟 7：為每個單字標記狀態，並轉換成 DTO ==========
        Map<Long, String> stateMap = new HashMap<>();
        s0Words.forEach(w -> stateMap.put(w.getId(), "S0"));
        s_1Words.forEach(w -> stateMap.put(w.getId(), "S-1"));
        s1Words.forEach(w -> stateMap.put(w.getId(), "S1"));
        s2Words.forEach(w -> stateMap.put(w.getId(), "S2"));
        s3Words.forEach(w -> stateMap.put(w.getId(), "S3"));
        deduped.forEach(w -> stateMap.putIfAbsent(w.getId(), "S0"));

        // 隨機打亂順序（保持探索樂趣）
        Collections.shuffle(deduped);

        log.info("Final recommendation for user {}: {} words (S0={}, S-1={}, S1={}, S2={}, S3={})",
                user.getId(), deduped.size(),
                s0Words.size(), s_1Words.size(), s1Words.size(), s2Words.size(), s3Words.size());

        return deduped.stream()
                .map(w -> WordDTO.fromEntityWithState(w, stateMap.getOrDefault(w.getId(), "S0")))
                .collect(Collectors.toList());
    }

    // ==================== 公開方法：閱讀處理（Phase 6）====================

    /**
     * 處理閱讀事件（v2.0）
     *
     * 業務邏輯：
     * - 調用 Phase 3 閱讀算法
     * - 記憶增益較小（ΔM = 0.01 ~ 0.05）
     * - 次數衰減效果（反覆閱讀增益遞減）
     *
     * @param user 使用者
     * @param wordId 單字 ID
     * @param durationSeconds 閱讀時長（秒）
     * @return 更新後的 WordState
     */
    @Transactional
    public WordState handleReadingEvent(User user, Long wordId, double durationSeconds) {

        // 🔑 添加唯一請求 ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        log.info("🟢 [{}] handleReadingEvent START: user={}, wordId={}, duration={}s",
                requestId, user.getId(), wordId, durationSeconds);

        // ========== 步驟 1：查詢單字和狀態 ==========
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> {
                    log.error("🔴 [{}] Word not found: wordId={}", requestId, wordId);
                    return new RuntimeException("Word not found: " + wordId);
                });

        log.debug("🟢 [{}] Word found: {}", requestId, word.getWordText());

        WordState state = wordStateRepository.findByUserAndWord(user, word)
                .orElseGet(() -> {
                    log.debug("🟢 [{}] WordState not found, initializing new state", requestId);
                    return initializeNewState(user, word);
                });

        log.debug("🟢 [{}] Current state: {}, strength: {}, readCount: {}",
                requestId, state.getCurrentState(), state.getMemoryStrength(), state.getReadCount());

        LocalDateTime now = LocalDateTime.now();

        // ========== 步驟 2：記錄閱讀前的狀態 ==========
        String previousState = state.getCurrentState();
        double previousStrength = state.getMemoryStrength();
        int previousReadCount = state.getReadCount();
        boolean prevEver = Boolean.TRUE.equals(state.getHasEverLearned());

        // ========== 步驟 3：調用 Phase 3 閱讀算法 ==========
        double newStrength = algorithmCoreService.calculateNewMemoryStrengthFromReading(
                state, word, durationSeconds, now
        );

        log.debug("🟢 [{}] Memory strength: {:.3f} → {:.3f}",
                requestId, previousStrength, newStrength);

        // ========== 步驟 4：判定新的 FSM 狀態 ==========
        String newState = algorithmCoreService.determineFsmState(
                newStrength,
                state.getHasEverLearned()
        );

        log.debug("🟢 [{}] FSM state: {} → {}", requestId, previousState, newState);

        if ("S0".equals(previousState)) {
            newState = "S1";
            if (!Boolean.TRUE.equals(state.getHasEverLearned())) {
                state.setHasEverLearned(true);
            }
            log.info("🟢 [{}] Promote by reading: S0 → S1, hasEverLearned set to true", requestId);
        }

        // ========== 步驟 5：更新 WordState 的核心欄位 ==========
        state.setMemoryStrength(newStrength);
        state.setCurrentState(newState);
        state.setLastReviewTime(now);
        state.setLastReadTime(now);

        // ========== 步驟 6：更新閱讀統計 ==========
        int newCount = state.getReadCount() + 1;
        state.setReadCount(newCount);

        double newTotal = state.getTotalReadDuration() + durationSeconds;
        state.setTotalReadDuration(newTotal);

        double newAvg = newTotal / newCount;
        state.setAvgReadDuration(newAvg);

        log.debug("🟢 [{}] Reading statistics: count: {}→{}, total: {:.1f}s, avg: {:.1f}s",
                requestId, previousReadCount, newCount, newTotal, newAvg);

        // ========== 步驟 7：保存歷史記錄 ==========
        log.info("🟢 [{}] Saving review history...", requestId);

        ReviewHistory history = new ReviewHistory();
        history.setUser(user);
        history.setWord(word);
        history.setInteractionType(InteractionType.READ);
        history.setReviewTime(now);
        history.setDurationMs((long)(durationSeconds * 1000));
        history.setIsCorrect(null);

        ReviewHistory savedHistory = reviewHistoryRepository.save(history);

        log.info("🟢 [{}] Review history saved: id={}", requestId, savedHistory.getId());

        // ========== 步驟 8：保存並返回 ==========
        log.info("🟢 [{}] Saving WordState...", requestId);

        WordState saved = wordStateRepository.save(state);

        log.info("🟢 [{}] handleReadingEvent END: word='{}', duration={:.1f}s, " +
                        "strength: {:.3f}→{:.3f}, state: {}→{}, read_count: {}→{}",
                requestId, word.getWordText(), durationSeconds,
                previousStrength, newStrength,
                previousState, newState,
                previousReadCount, newCount);

        return saved;
    }

    // ==================== 公開方法：學習統計 ====================

    /**
     * 獲取學習統計摘要
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLearningStatsSummary(User user) {
        Map<String, Object> stats = new HashMap<>();

        List<Object[]> stateStats = wordStateRepository.countByUserGroupByState(user);

        long totalS0 = 0, totalS_1 = 0, totalS1 = 0, totalS2 = 0, totalS3 = 0;
        for (Object[] row : stateStats) {
            String state = (String) row[0];
            Long count = (Long) row[1];

            switch (state) {
                case "S0":  totalS0 = count; break;
                case "S-1": totalS_1 = count; break;
                case "S1":  totalS1 = count; break;
                case "S2":  totalS2 = count; break;
                case "S3":  totalS3 = count; break;
            }
        }

        stats.put("newWords", totalS0);
        stats.put("forgottenWords", totalS_1);
        stats.put("learningWords", totalS1);
        stats.put("reviewingWords", totalS2);
        stats.put("masteredWords", totalS3);
        stats.put("totalLearned", totalS_1 + totalS1 + totalS2 + totalS3);

        log.debug("Learning stats for user {}: {}", user.getId(), stats);

        return stats;
    }

    // ==================== v2.0 輔助方法 ====================

    /**
     * 使用輕度優先度排序獲取單字
     */
    private List<Word> fetchWordsWithPriority(
            User user,
            String state,
            int targetCount,
            LocalDateTime currentTime,
            Supplier<List<WordState>> fetcher) {

        if (targetCount <= 0) {
            return new ArrayList<>();
        }

        List<WordState> candidates = fetcher.get();

        if (candidates.isEmpty()) {
            log.debug("No {} words found for user {}", state, user.getId());
            return new ArrayList<>();
        }

        if (candidates.size() <= targetCount) {
            log.debug("Limited {} candidates ({}), return all", state, candidates.size());
            return candidates.stream()
                    .map(WordState::getWord)
                    .collect(Collectors.toList());
        }

        Map<Long, Double> priorities = new HashMap<>();
        for (WordState ws : candidates) {
            double priority = algorithmCoreService.calculateReviewPriority(
                    ws, ws.getWord(), currentTime
            );
            priorities.put(ws.getWord().getId(), priority);
        }

        List<WordState> sorted = candidates.stream()
                .sorted((a, b) -> {
                    double priorityA = priorities.getOrDefault(a.getWord().getId(), 0.0);
                    double priorityB = priorities.getOrDefault(b.getWord().getId(), 0.0);
                    return Double.compare(priorityB, priorityA);
                })
                .collect(Collectors.toList());

        int topCount = Math.max((int)(sorted.size() * 0.6), targetCount);
        List<WordState> topPriority = sorted.subList(0, Math.min(topCount, sorted.size()));

        Collections.shuffle(topPriority);

        List<Word> result = topPriority.stream()
                .limit(targetCount)
                .map(WordState::getWord)
                .collect(Collectors.toList());

        log.debug("Selected {} {} words from {} candidates (top 60% then random)",
                result.size(), state, candidates.size());

        return result;
    }

    /**
     * 初始化新的 WordState
     */
    private WordState initializeNewState(User user, Word word) {
        WordState state = new WordState();

        state.setUser(user);
        state.setWord(word);

        state.setMemoryStrength(0.0);
        state.setCurrentState("S0");
        state.setHasEverLearned(false);

        state.setTotalCorrect(0);
        state.setTotalIncorrect(0);
        state.setAverageResponseTimeMs(0L);

        state.setReadCount(0);
        state.setTotalReadDuration(0.0);
        state.setAvgReadDuration(0.0);

        state.setForgottenCount(0);
        state.setLastForgottenTime(null);

        LocalDateTime now = LocalDateTime.now();
        state.setLastReviewTime(now);
        state.setLastReadTime(null);

        state.setNextReviewPriority(0.0);

        log.debug("Initialized new WordState: user={}, word='{}', state=S0, strength=0.0",
                user.getId(), word.getWordText());

        return state;
    }

    // ==================== 原有工具方法 ====================

    private Map<String, Integer> distributeCounts(int total, Map<String, Double> ratios) {
        Map<String, Integer> result = new LinkedHashMap<>();

        if (total <= 0 || ratios == null || ratios.isEmpty()) {
            if (ratios != null) ratios.keySet().forEach(k -> result.put(k, 0));
            return result;
        }

        double sum = ratios.values().stream().mapToDouble(Double::doubleValue).sum();

        if (sum <= 0) {
            ratios.keySet().forEach(k -> result.put(k, 0));
            return result;
        }

        class Part {
            String key;
            double frac;
            Part(String k, double f) {
                key = k;
                frac = f;
            }
        }

        List<Part> fracs = new ArrayList<>();
        int allocated = 0;

        for (Map.Entry<String, Double> e : ratios.entrySet()) {
            double exact = total * (e.getValue() / sum);
            int base = (int) Math.floor(exact);
            double rem = exact - base;
            result.put(e.getKey(), base);
            fracs.add(new Part(e.getKey(), rem));
            allocated += base;
        }

        int remain = total - allocated;
        fracs.sort((a, b) -> Double.compare(b.frac, a.frac));

        for (int i = 0; i < remain && i < fracs.size(); i++) {
            String k = fracs.get(i).key;
            result.put(k, result.get(k) + 1);
        }

        return result;
    }

    private Pageable page(int size) {
        return PageRequest.of(0, Math.max(1, size));
    }

    private List<Word> deduplicateById(List<Word> list) {
        Set<Long> seen = new HashSet<>();
        List<Word> out = new ArrayList<>(list.size());

        for (Word w : list) {
            if (w == null || w.getId() == null) continue;
            if (seen.add(w.getId())) {
                out.add(w);
            }
        }

        return out;
    }

    private double sigmoid(double x, double x0, double k) {
        return 1.0 / (1.0 + Math.exp(-k * (x - x0)));
    }
}