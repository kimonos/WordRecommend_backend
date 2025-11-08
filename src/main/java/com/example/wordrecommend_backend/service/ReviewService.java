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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final AlgorithmCoreService algorithmCoreService;

    // ==================== 公開方法：答題處理 ====================

    /**
     * 處理答題回饋（v3.0 - 完全在 Service 層比對）
     */
    @Transactional
    public WordState handleQuizAnswer(
            User user,
            Long wordId,
            String selectedAnswer,
            long answerTimeMs,
            String questionType) {

        log.info("🔵 ========== 答題處理開始 ==========");
        log.info("使用者: {}, 單字: {}, 題型: {}", user.getId(), wordId, questionType);

        // ========== 步驟 1：查詢單字和狀態 ==========
        Word word = findWordById(wordId);
        WordState state = findOrInitializeState(user, word);
        LocalDateTime now = LocalDateTime.now();

        log.debug("單字: {}, 詞性: {}", word.getWordText(), word.getPartOfSpeech());

        // ========== 步驟 2：在 Service 層進行答案比對 ==========
        boolean isCorrect = validateAnswerByQuestionType(word, selectedAnswer, questionType);

        log.info("🟠 答案比對結果: isCorrect={}", isCorrect);

        // ========== 步驟 3：記錄答題前的狀態 ==========
        String previousState = state.getCurrentState();
        double previousStrength = state.getMemoryStrength();

        // ========== 步驟 4：調用演算法計算新記憶強度 ==========
        double newStrength = algorithmCoreService.calculateNewMemoryStrength(
                state, word, isCorrect, answerTimeMs, now
        );

        // ========== 步驟 5：判定新的 FSM 狀態 ==========
        String newState = algorithmCoreService.determineFsmState(
                newStrength,
                state.getHasEverLearned()
        );

        // ========== 步驟 6：檢測是否遺忘 ==========
        boolean forgotten = detectForgetting(state, newState, previousState);

        if (forgotten) {
            state.setForgottenCount(state.getForgottenCount() + 1);
            state.setLastForgottenTime(now);
            log.warn("🔴 遺忘偵測: 使用者 {} 忘記了單字 '{}' (遺忘次數: {})",
                    user.getId(), word.getWordText(), state.getForgottenCount());
        }

        // ========== 步驟 7：更新 WordState 的核心欄位 ==========
        state.setMemoryStrength(newStrength);
        state.setCurrentState(newState);
        state.setLastReviewTime(now);
        state.setHasEverLearned(true);

        // ========== 步驟 8：更新答題統計 ==========
        updateAnswerStatistics(state, isCorrect, answerTimeMs);

        // ========== 步驟 9：計算新的推薦優先度 ==========
        double priority = algorithmCoreService.calculateReviewPriority(
                state, word, now
        );
        state.setNextReviewPriority(priority);

        // ========== 步驟 10：保存歷史記錄 ==========
        saveReviewHistory(user, word, InteractionType.QUIZ, answerTimeMs, isCorrect, now);

        // ========== 步驟 11：保存並返回 ==========
        WordState saved = wordStateRepository.save(state);

        log.info("✅ ========== 答題處理完成 ==========");
        log.info("結果: isCorrect={}, strength: {:.4f}→{:.4f}, state: {}→{}, forgotten={}",
                isCorrect, previousStrength, newStrength,
                previousState, newState, forgotten);

        return saved;
    }

    /**
     * 重載方法：保持向後相容性
     */
    @Transactional
    public WordState handleQuizAnswer(
            User user,
            Long wordId,
            String selectedAnswer,
            long answerTimeMs) {

        return handleQuizAnswer(user, wordId, selectedAnswer, answerTimeMs, "HARD");
    }

    // ==================== 核心邏輯：答案比對 ====================

    /**
     * 根據題型驗證使用者的答案
     */
    public boolean validateAnswerByQuestionType(
            Word word,
            String selectedAnswer,
            String questionType) {

        log.debug("🔵 開始答案驗證: type={}, answer='{}'",
                questionType, selectedAnswer);

        if (selectedAnswer == null || word == null) {
            log.error("❌ 答案或單字為 null");
            return false;
        }

        switch (questionType) {
            case "EASY":
                return validateEasyAnswer(word, selectedAnswer);
            case "NORMAL":
                return validateNormalAnswer(word, selectedAnswer);
            case "HARD":
                return validateHardAnswer(word, selectedAnswer);
            default:
                log.error("❌ 未知題型: {}", questionType);
                return false;
        }
    }

    /**
     * 驗證簡單題（英 → 中選擇）
     */
    private boolean validateEasyAnswer(Word mainWord, String selectedAnswer) {

        log.debug("🟡 驗證 EASY 題型");

        try {
            Long selectedWordId = Long.parseLong(selectedAnswer.trim());

            Word selectedWord = wordRepository.findById(selectedWordId)
                    .orElseThrow(() -> {
                        log.error("❌ EASY: 找不到選項 Word ID: {}", selectedWordId);
                        return new RuntimeException("Word not found: " + selectedWordId);
                    });

            String mainTranslation = mainWord.getTranslation().trim();
            String selectedTranslation = selectedWord.getTranslation().trim();

            boolean isCorrect = mainTranslation.equals(selectedTranslation);

            log.info("✅ EASY 驗證: 主題='{}', 選擇='{}' = {}",
                    mainTranslation, selectedTranslation, isCorrect);

            return isCorrect;

        } catch (NumberFormatException e) {
            log.error("❌ EASY: 無效的 Word ID 格式: '{}', error: {}",
                    selectedAnswer, e.getMessage());
            return false;
        }
    }

    /**
     * 驗證普通題（中 → 英選擇）
     */
    private boolean validateNormalAnswer(Word mainWord, String selectedAnswer) {

        log.debug("🟡 驗證 NORMAL 題型");

        try {
            Long selectedWordId = Long.parseLong(selectedAnswer.trim());
            Long mainWordId = mainWord.getId();

            boolean isCorrect = selectedWordId.equals(mainWordId);

            log.info("✅ NORMAL 驗證: 主題ID={}, 選擇ID={} = {}",
                    mainWordId, selectedWordId, isCorrect);

            return isCorrect;

        } catch (NumberFormatException e) {
            log.error("❌ NORMAL: 無效的 Word ID 格式: '{}', error: {}",
                    selectedAnswer, e.getMessage());
            return false;
        }
    }

    /**
     * 驗證困難題（中 → 英拼寫）
     */
    private boolean validateHardAnswer(Word mainWord, String selectedAnswer) {

        log.debug("🟡 驗證 HARD 題型");

        String correctSpelling = mainWord.getWordText().trim().toLowerCase();
        String userSpelling = selectedAnswer.trim().toLowerCase();

        boolean isCorrect = correctSpelling.equals(userSpelling);

        log.info("✅ HARD 驗證: 正確='{}', 使用者='{}' = {}",
                correctSpelling, userSpelling, isCorrect);

        return isCorrect;
    }

    // ==================== 公開方法：複習單字推薦 ====================

    /**
     * 獲取複習單字（改進版 - 支援排除已推薦的單字）
     *
     * 🔑 新增參數：excludeWordIds
     * - 用於防止同一複習會話中重複推薦同一個單字
     *
     * @param user 使用者
     * @param limit 推薦數量
     * @param excludeWordIds 排除的單字 ID 集合
     * @return 複習單字列表（不含已排除的單字）
     */
    @Transactional(readOnly = true)
    public List<WordDTO> getReviewWords(User user, int limit, Set<Long> excludeWordIds) {

        log.info("開始為使用者 {} 生成複習推薦（limit={}, excludeCount={}）",
                user.getId(), limit, excludeWordIds.size());

        if (limit <= 0) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();

        // ========== 獲取所有可複習的單字 ==========

        List<WordState> allReviewable = new ArrayList<>();

        allReviewable.addAll(wordStateRepository.findForgottenWords(user, PageRequest.of(0, 1000)));
        allReviewable.addAll(wordStateRepository.findByUserAndState(user, "S1", PageRequest.of(0, 1000)));
        allReviewable.addAll(wordStateRepository.findByUserAndState(user, "S2", PageRequest.of(0, 1000)));
        allReviewable.addAll(wordStateRepository.findByUserAndState(user, "S3", PageRequest.of(0, 1000)));

        log.debug("可複習單字總數: {}", allReviewable.size());

        // ========== 排除已推薦過的單字 ==========

        List<WordState> filtered = allReviewable.stream()
                .filter(ws -> !excludeWordIds.contains(ws.getWord().getId()))
                .collect(Collectors.toList());

        log.debug("排除已推薦後: {} 個", filtered.size());

        // ========== 計算優先度並排序 ==========

        List<ScoredWordState> scored = filtered.stream()
                .map(ws -> new ScoredWordState(
                        ws,
                        algorithmCoreService.calculateReviewPriority(ws, ws.getWord(), now)
                ))
                .sorted((a, b) -> Double.compare(b.priority, a.priority))
                .collect(Collectors.toList());

        // ========== 取出前 N 個 ==========

        List<WordDTO> result = scored.stream()
                .limit(limit)
                .map(sw -> WordDTO.fromEntityWithState(sw.wordState.getWord(), sw.wordState.getCurrentState()))
                .collect(Collectors.toList());

        log.info("✅ 複習推薦完成: {} 個不同的單字", result.size());

        return result;
    }

    /**
     * 舊的重載方法（向後相容）
     */
    @Transactional(readOnly = true)
    public List<WordDTO> getReviewWords(User user, int limit) {
        return getReviewWords(user, limit, new HashSet<>());
    }

    /**
     * 查詢使用者對某個單字的狀態
     */
    public WordState findWordStateByWordId(User user, Long wordId) {
        Word word = wordRepository.findById(wordId)
                .orElse(null);

        if (word == null) {
            return null;
        }

        return wordStateRepository.findByUserAndWord(user, word)
                .orElse(null);
    }

    /**
     * 獲取複習準備狀態（詳細資訊）
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

        String suggestion = (totalReviewable >= MIN_WORDS_TO_REVIEW)
                ? "你已經可以開始複習了！"
                : String.format("再學習 %d 個單字後，即可開始複習！", MIN_WORDS_TO_REVIEW - totalReviewable);
        readiness.put("suggestion", suggestion);

        return readiness;
    }

    // ==================== 私有方法：遺忘檢測 ====================

    private boolean detectForgetting(WordState state, String newState, String previousState) {

        if (!"S-1".equals(newState)) {
            return false;
        }

        if ("S-1".equals(previousState)) {
            return false;
        }

        if (!state.getHasEverLearned()) {
            return false;
        }

        if ("S0".equals(previousState)) {
            return false;
        }

        log.debug("🔴 遺忘偵測成功: {} → S-1", previousState);
        return true;
    }

    // ==================== 私有方法：統計更新 ====================

    private void updateAnswerStatistics(WordState state, boolean isCorrect, long answerTimeMs) {

        if (isCorrect) {
            state.setTotalCorrect(state.getTotalCorrect() + 1);
        } else {
            state.setTotalIncorrect(state.getTotalIncorrect() + 1);
        }

        int totalAnswers = state.getTotalCorrect() + state.getTotalIncorrect();

        if (totalAnswers > 0) {
            long previousAvg = state.getAverageResponseTimeMs();
            long newAvg = ((previousAvg * (totalAnswers - 1)) + answerTimeMs) / totalAnswers;
            state.setAverageResponseTimeMs(newAvg);
        } else {
            state.setAverageResponseTimeMs(answerTimeMs);
        }
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
                .orElseGet(() -> initializeNewState(user, word));
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
        state.setLastReviewTime(LocalDateTime.now());
        state.setLastReadTime(null);
        state.setNextReviewPriority(0.0);

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
    }

    // ==================== 輔助類別 ====================

    private static class ScoredWordState {
        WordState wordState;
        double priority;

        ScoredWordState(WordState ws, double priority) {
            this.wordState = ws;
            this.priority = priority;
        }
    }
}