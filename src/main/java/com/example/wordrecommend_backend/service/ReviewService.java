package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.*;
import com.example.wordrecommend_backend.repository.ReviewHistoryRepository;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    // ==================== 依賴注入 ====================

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final AlgorithmCoreService algorithmCoreService;

    // ==================== 公開方法：答題處理（v2.1 - 後端判定答案）====================

    /**
     * 處理答題回饋（v2.1 - 後端判定答案）
     *
     * 修改：
     * - 新增參數：selectedAnswer（使用者選擇的答案）
     * - 後端判定 isCorrect
     *
     * 核心流程：
     * 1. 查詢或初始化 WordState
     * 2. 後端判定答案是否正確
     * 3. 使用 Phase 3 算法計算新記憶強度
     * 4. 判定新的 FSM 狀態
     * 5. 檢測是否遺忘（S-1）
     * 6. 更新所有統計資料
     * 7. 保存歷史記錄
     *
     * @param user 使用者
     * @param wordId 單字 ID
     * @param selectedAnswer 使用者選擇的答案
     * @param answerTimeMs 答題時長（毫秒）
     * @return 更新後的 WordState
     */
    @Transactional
    public WordState handleQuizAnswer(User user, Long wordId, String selectedAnswer, long answerTimeMs) {

        // ========== 步驟 1：查詢單字和狀態 ==========
        Word word = findWordById(wordId);
        WordState state = findOrInitializeState(user, word);
        LocalDateTime now = LocalDateTime.now();

        // ========== 步驟 2：後端判定答案是否正確 ==========
        boolean isCorrect = isAnswerCorrect(word, selectedAnswer);

        log.debug("答案判定: wordId={}, correctAnswer='{}', selectedAnswer='{}', isCorrect={}",
                wordId, word.getTranslation(), selectedAnswer, isCorrect);

        // ========== 步驟 3：記錄答題前的狀態 ==========
        String previousState = state.getCurrentState();
        double previousStrength = state.getMemoryStrength();

        log.debug("Quiz answer for user {} word '{}': correct={}, time={}ms, " +
                        "current state={}, strength={:.3f}",
                user.getId(), word.getWordText(), isCorrect, answerTimeMs,
                previousState, previousStrength);

        // ========== 步驟 4：調用 Phase 3 算法計算新記憶強度 ==========
        double newStrength = algorithmCoreService.calculateNewMemoryStrength(
                state, word, isCorrect, answerTimeMs, now
        );

        log.debug("Memory strength calculation: {:.3f} → {:.3f}",
                previousStrength, newStrength);

        // ========== 步驟 5：判定新的 FSM 狀態 ==========
        String newState = algorithmCoreService.determineFsmState(
                newStrength,
                state.getHasEverLearned()
        );

        log.debug("FSM state transition: {} → {}", previousState, newState);

        // ========== 步驟 6：檢測是否遺忘 ==========
        boolean forgotten = detectForgetting(state, newState, previousState);

        if (forgotten) {
            state.setForgottenCount(state.getForgottenCount() + 1);
            state.setLastForgottenTime(now);

            log.warn("🔴 User {} forgot word '{}' (forgotten count: {})",
                    user.getId(), word.getWordText(), state.getForgottenCount());
        }

        // ========== 步驟 7：更新 WordState 的核心欄位 ==========
        state.setMemoryStrength(newStrength);
        state.setCurrentState(newState);
        state.setLastReviewTime(now);
        state.setHasEverLearned(true);  // 🔑 答題才設置為 true

        // ========== 步驟 8：更新答題統計 ==========
        updateAnswerStatistics(state, isCorrect, answerTimeMs);

        // ========== 步驟 9：計算新的推薦優先度 ==========
        double priority = algorithmCoreService.calculateReviewPriority(
                state, word, now
        );
        state.setNextReviewPriority(priority);

        log.debug("Review priority updated: {:.2f}", priority);

        // ========== 步驟 10：保存歷史記錄 ==========
        saveReviewHistory(user, word, InteractionType.QUIZ, answerTimeMs, isCorrect, now);

        // ========== 步驟 11：保存並返回 ==========
        WordState saved = wordStateRepository.save(state);

        log.info("✅ Quiz processed for user {} word '{}': " +
                        "result={}, time={}ms, strength: {:.3f}→{:.3f}, state: {}→{}, " +
                        "forgotten={}, priority={:.2f}",
                user.getId(), word.getWordText(),
                isCorrect ? "CORRECT" : "INCORRECT", answerTimeMs,
                previousStrength, newStrength,
                previousState, newState,
                forgotten, priority);

        return saved;
    }

    // ==================== 公開方法：複習單字推薦（v2.0 - Phase 6.9）====================

    /**
     * 獲取複習單字（複習模式 - 純複習，不含新單字）
     *
     * 與探索模式的區別：
     * - 探索模式（RecommendationService）：包含新單字（S0），用於「學習新單字」
     * - 複習模式（ReviewService）：只包含已學單字（S-1, S1, S2, S3），用於「開始複習」
     *
     * 推薦策略：
     * - S-1（遺忘）：最高優先度（100%）
     * - S1（學習中）：高優先度（按 nextReviewPriority 排序）
     * - S2（複習中）：中優先度
     * - S3（已精通）：低優先度（維持記憶）
     *
     * 🔑 核心特點：
     * - 絕對不包含新單字（S0）
     * - 如果已學單字數量不足 limit，只返回實際數量（不遞補 S0）
     * - 使用 Phase 3 的 calculateReviewPriority() 計算優先度
     *
     * @param user 使用者
     * @param limit 推薦數量
     * @return 複習單字列表（不含 S0）
     */
    @Transactional(readOnly = true)
    public List<WordDTO> getReviewWords(User user, int limit) {

        log.info("開始為使用者 {} 生成複習推薦（limit={}）", user.getId(), limit);

        if (limit <= 0) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<WordDTO> recommendations = new ArrayList<>();

        // ========== 步驟 1：獲取所有可複習的單字 ==========

        // 1.1 遺忘單字（S-1）- 最高優先度（必須優先複習）
        List<WordState> forgottenWords = wordStateRepository.findForgottenWords(
                user, PageRequest.of(0, 100)
        );

        // 1.2 學習中單字（S1）- 高優先度
        List<WordState> learningWords = wordStateRepository.findByUserAndState(
                user, "S1", PageRequest.of(0, 100)
        );

        // 1.3 複習中單字（S2）- 中優先度
        List<WordState> reviewingWords = wordStateRepository.findByUserAndState(
                user, "S2", PageRequest.of(0, 100)
        );

        // 1.4 已精通單字（S3）- 低優先度（維持記憶）
        List<WordState> masteredWords = wordStateRepository.findByUserAndState(
                user, "S3", PageRequest.of(0, 100)
        );

        log.debug("可複習單字統計: S-1={}, S1={}, S2={}, S3={}",
                forgottenWords.size(), learningWords.size(),
                reviewingWords.size(), masteredWords.size());

        // ========== 步驟 2：計算所有單字的優先度 ==========

        List<ScoredWordState> scoredWords = new ArrayList<>();

        // 2.1 遺忘單字：最高優先度
        for (WordState ws : forgottenWords) {
            double priority = algorithmCoreService.calculateReviewPriority(
                    ws, ws.getWord(), now
            );
            scoredWords.add(new ScoredWordState(ws, priority));
        }

        // 2.2 學習中單字：高優先度
        for (WordState ws : learningWords) {
            double priority = algorithmCoreService.calculateReviewPriority(
                    ws, ws.getWord(), now
            );
            scoredWords.add(new ScoredWordState(ws, priority));
        }

        // 2.3 複習中單字：中優先度
        for (WordState ws : reviewingWords) {
            double priority = algorithmCoreService.calculateReviewPriority(
                    ws, ws.getWord(), now
            );
            scoredWords.add(new ScoredWordState(ws, priority));
        }

        // 2.4 已精通單字：低優先度
        for (WordState ws : masteredWords) {
            double priority = algorithmCoreService.calculateReviewPriority(
                    ws, ws.getWord(), now
            );
            scoredWords.add(new ScoredWordState(ws, priority));
        }

        log.debug("計算了 {} 個單字的優先度", scoredWords.size());

        // ========== 步驟 3：按優先度排序（降序） ==========

        scoredWords.sort((a, b) -> Double.compare(b.priority, a.priority));

        // ========== 步驟 4：選取前 N 個單字 ==========

        int selected = 0;
        for (ScoredWordState scored : scoredWords) {
            if (selected >= limit) break;

            WordState ws = scored.wordState;
            Word word = ws.getWord();

            // 轉換為 DTO
            WordDTO dto = WordDTO.fromEntityWithState(word, ws.getCurrentState());
            recommendations.add(dto);
            selected++;

            log.trace("選擇複習單字: {} (state={}, priority={:.2f})",
                    word.getWordText(), ws.getCurrentState(), scored.priority);
        }

        // ========== 步驟 5：結果驗證與日誌 ==========

        if (recommendations.size() < limit) {
            log.warn("⚠️ 複習單字數量不足: 期望={}, 實際={}, 不遞補新單字（S0）",
                    limit, recommendations.size());
            log.info("💡 建議使用者繼續學習新單字後再複習");
        } else {
            log.info("✅ 複習推薦完成: 返回 {} 個單字", recommendations.size());
        }

        // ========== 步驟 6：輕度隨機打亂（保持部分探索性） ==========

        int topCount = (int)(recommendations.size() * 0.3);

        if (topCount > 0 && recommendations.size() > topCount) {
            List<WordDTO> top = new ArrayList<>(recommendations.subList(0, topCount));
            List<WordDTO> rest = new ArrayList<>(recommendations.subList(topCount, recommendations.size()));
            Collections.shuffle(rest);

            recommendations.clear();
            recommendations.addAll(top);
            recommendations.addAll(rest);

            log.debug("輕度隨機打亂: 前 {} 個保持順序，後 {} 個打亂",
                    topCount, rest.size());
        }

        log.info("為使用者 {} 生成複習推薦完成: {} 個單字（不含新單字）",
                user.getId(), recommendations.size());

        return recommendations;
    }

    // ==================== 公開方法：複習準備檢查（Phase 6.8）====================

    /**
     * 檢查使用者是否有足夠的單字可以複習
     *
     * 規則：
     * - 至少需要學習 10 個單字（S-1, S1, S2, S3）
     * - 如果單字數不足，建議繼續學習新單字
     *
     * @param user 使用者
     * @return 是否可以開始複習
     */
//    @Transactional(readOnly = true)
//    public boolean canStartReview(User user) {
//        long countS_1 = wordStateRepository.countForgottenWords(user);
//        long countS1 = wordStateRepository.countByUserAndState(user, "S1");
//        long countS2 = wordStateRepository.countByUserAndState(user, "S2");
//        long countS3 = wordStateRepository.countByUserAndState(user, "S3");
//
//        long totalReviewable = countS_1 + countS1 + countS2 + countS3;
//
//        final int MIN_WORDS_TO_REVIEW = 10;
//
//        boolean canReview = totalReviewable >= MIN_WORDS_TO_REVIEW;
//
//        log.debug("User {} review readiness: {} reviewable words (min: {}), canReview: {}",
//                user.getId(), totalReviewable, MIN_WORDS_TO_REVIEW, canReview);
//
//        return canReview;
//    }

    /**
     * 獲取複習準備狀態（詳細資訊）
     *
     * @param user 使用者
     * @return 複習準備狀態
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewReadiness(User user) {
        Map<String, Object> readiness = new HashMap<>();

        long countS_1 = wordStateRepository.countForgottenWords(user);
        long countS1 = wordStateRepository.countByUserAndState(user, "S1");
        long countS2 = wordStateRepository.countByUserAndState(user, "S2");
        long countS3 = wordStateRepository.countByUserAndState(user, "S3");

        long totalReviewable = countS_1 + countS1 + countS2 + countS3;

        final int MIN_WORDS_TO_REVIEW = 10;

        readiness.put("canReview", totalReviewable >= MIN_WORDS_TO_REVIEW);
        readiness.put("totalReviewable", totalReviewable);
        readiness.put("minRequired", MIN_WORDS_TO_REVIEW);
        readiness.put("remaining", Math.max(0, MIN_WORDS_TO_REVIEW - totalReviewable));

        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("forgotten", countS_1);
        breakdown.put("learning", countS1);
        breakdown.put("reviewing", countS2);
        breakdown.put("mastered", countS3);
        readiness.put("breakdown", breakdown);

        String suggestion;
        if (totalReviewable >= MIN_WORDS_TO_REVIEW) {
            suggestion = "你已經可以開始複習了！";
        } else {
            long need = MIN_WORDS_TO_REVIEW - totalReviewable;
            suggestion = String.format("再學習 %d 個單字後，即可開始複習！", need);
        }
        readiness.put("suggestion", suggestion);

        log.info("Review readiness for user {}: canReview={}, total={}, breakdown={}",
                user.getId(), readiness.get("canReview"), totalReviewable, breakdown);

        return readiness;
    }

    // ==================== 私有方法：答案判定 ====================

    /**
     * 判定使用者的答案是否正確
     *
     * 比對邏輯：
     * 1. 移除首尾空格
     * 2. 只比對分號前的第一個翻譯（處理多義詞）
     *
     * @param word 單字實體
     * @param selectedAnswer 使用者選擇的答案
     * @return 是否正確
     */
    /**
     * 判定使用者的答案是否正確（v2.3 - 精確比對版）
     *
     * 策略：
     * - 不做任何正規化（不切分、不替換）
     * - 只移除首尾空格
     * - 完全匹配
     *
     * @param word 單字實體
     * @param selectedAnswer 使用者選擇的答案
     * @return 是否正確
     */
    private boolean isAnswerCorrect(Word word, String selectedAnswer) {
        if (selectedAnswer == null || word.getTranslation() == null) {
            log.warn("❌ 答案或翻譯為 null: selectedAnswer={}, translation={}, wordId={}",
                    selectedAnswer, word.getTranslation(), word.getId());
            return false;
        }

        // 🔑 只移除首尾空格，不做其他處理
        String normalizedSelected = selectedAnswer.trim();
        String normalizedCorrect = word.getTranslation().trim();

        boolean isCorrect = normalizedSelected.equals(normalizedCorrect);

        // 詳細日誌
        if (!isCorrect) {
            log.warn("❌ 答案比對失敗:");
            log.warn("   wordId: {}, word: '{}'", word.getId(), word.getWordText());
            log.warn("   選擇的答案: '{}' (length={})", normalizedSelected, normalizedSelected.length());
            log.warn("   正確答案:   '{}' (length={})", normalizedCorrect, normalizedCorrect.length());
            log.warn("   選擇 Unicode: {}", toUnicodeString(normalizedSelected));
            log.warn("   正確 Unicode: {}", toUnicodeString(normalizedCorrect));

            // 🔑 顯示差異位置
            int minLength = Math.min(normalizedSelected.length(), normalizedCorrect.length());
            for (int i = 0; i < minLength; i++) {
                if (normalizedSelected.charAt(i) != normalizedCorrect.charAt(i)) {
                    log.warn("   第一個差異在位置 {}: '{}' vs '{}'",
                            i, normalizedSelected.charAt(i), normalizedCorrect.charAt(i));
                    break;
                }
            }
        } else {
            log.debug("✅ 答案比對成功: wordId={}, word='{}', answer='{}'",
                    word.getId(), word.getWordText(), normalizedSelected);
        }

        return isCorrect;
    }

    /**
     * 將字串轉換為 Unicode 表示（用於除錯）
     */
    private String toUnicodeString(String str) {
        if (str == null || str.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            if (i > 0) sb.append(" ");
            char c = str.charAt(i);
            sb.append(String.format("U+%04X", (int) c));
            // 🔑 顯示字符類別
            if (Character.isWhitespace(c)) {
                sb.append("(space)");
            } else if (c < 32 || c == 127) {
                sb.append("(control)");
            }
        }
        return sb.toString();
    }

    // ==================== 私有方法：遺忘檢測 ====================

    private boolean detectForgetting(WordState state, String newState, String previousState) {

        if (!"S-1".equals(newState)) {
            log.trace("Not forgotten: newState is {}, not S-1", newState);
            return false;
        }

        if ("S-1".equals(previousState)) {
            log.trace("Not forgotten: already in S-1 state");
            return false;
        }

        if (!state.getHasEverLearned()) {
            log.trace("Not forgotten: never learned before (new word)");
            return false;
        }

        if ("S0".equals(previousState) && !state.getHasEverLearned()) {
            log.trace("Not forgotten: S0 state with hasEverLearned=false");
            return false;
        }

        log.debug("🔴 Forgetting detected: {} → S-1 (hasEverLearned=true)", previousState);
        return true;
    }

    // ==================== 私有方法：統計更新 ====================

    private void updateAnswerStatistics(WordState state, boolean isCorrect, long answerTimeMs) {

        if (isCorrect) {
            int newCorrect = state.getTotalCorrect() + 1;
            state.setTotalCorrect(newCorrect);
            log.trace("Answer statistics: totalCorrect updated to {}", newCorrect);
        } else {
            int newIncorrect = state.getTotalIncorrect() + 1;
            state.setTotalIncorrect(newIncorrect);
            log.trace("Answer statistics: totalIncorrect updated to {}", newIncorrect);
        }

        int totalAnswers = state.getTotalCorrect() + state.getTotalIncorrect();

        if (totalAnswers > 0) {
            long previousAvg = state.getAverageResponseTimeMs();
            long newAvg = ((previousAvg * (totalAnswers - 1)) + answerTimeMs) / totalAnswers;
            state.setAverageResponseTimeMs(newAvg);

            log.trace("Answer statistics: averageResponseTimeMs updated: {}ms → {}ms (current: {}ms)",
                    previousAvg, newAvg, answerTimeMs);
        } else {
            state.setAverageResponseTimeMs(answerTimeMs);
            log.warn("Answer statistics: totalAnswers is 0, setting avg to current: {}ms", answerTimeMs);
        }

        double accuracy = totalAnswers > 0
                ? (double) state.getTotalCorrect() / totalAnswers
                : 0.0;

        log.debug("Answer statistics updated: correct={}, incorrect={}, total={}, " +
                        "accuracy={:.1f}%, avgTime={}ms",
                state.getTotalCorrect(), state.getTotalIncorrect(), totalAnswers,
                accuracy * 100, state.getAverageResponseTimeMs());
    }

    // ==================== 私有方法：資料存取 ====================

    private Word findWordById(Long wordId) {
        return wordRepository.findById(wordId)
                .orElseThrow(() -> {
                    log.error("Word not found: wordId={}", wordId);
                    return new RuntimeException("Word not found: " + wordId);
                });
    }

    private WordState findOrInitializeState(User user, Word word) {
        return wordStateRepository.findByUserAndWord(user, word)
                .orElseGet(() -> {
                    log.debug("WordState not found for user {} word '{}', initializing new state",
                            user.getId(), word.getWordText());
                    return initializeNewState(user, word);
                });
    }

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

    private void saveReviewHistory(User user, Word word, InteractionType type,
                                   long durationMs, Boolean isCorrect, LocalDateTime reviewTime) {

        ReviewHistory history = new ReviewHistory();

        history.setUser(user);
        history.setWord(word);
        history.setInteractionType(type);
        history.setReviewTime(reviewTime);

        history.setDurationMs(durationMs);
        history.setIsCorrect(isCorrect);

        reviewHistoryRepository.save(history);

        log.trace("Saved review history: user={}, word='{}', type={}, duration={}ms, correct={}",
                user.getId(), word.getWordText(), type, durationMs, isCorrect);
    }

    // ==================== 輔助類別 ====================

    /**
     * 帶優先度的 WordState（用於排序）
     */
    private static class ScoredWordState {
        WordState wordState;
        double priority;

        ScoredWordState(WordState ws, double priority) {
            this.wordState = ws;
            this.priority = priority;
        }
    }
}