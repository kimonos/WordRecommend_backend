package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.QuestionDTO;
import com.example.wordrecommend_backend.dto.QuestionOptionDTO;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
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

/**
 * 題目生成服務（Phase 7）
 *
 * 責任：
 * - 根據記憶強度選擇題型
 * - 生成符合詞性要求的選項
 * - 隨機打亂選項順序
 *
 * @author kimonos-test
 * @version 1.0
 * @since Phase 7
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionGenerationService {

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;

    // ==================== 公開方法：生成題目 ====================

    /**
     * 根據主題單字生成完整題目
     *
     * 流程：
     * 1. 根據記憶強度決定題型
     * 2. 根據題型生成相應的選項
     * 3. 隨機打亂選項順序
     * 4. 返回完整題目 DTO
     *
     * @param wordState 使用者對該單字的狀態
//     * @param user 使用者（用於查詢已學單字）
     * @return 完整題目 DTO
     */
    @Transactional(readOnly = true)
    public QuestionDTO generateQuestion(WordState wordState, org.springframework.security.core.Authentication auth) {

        Word word = wordState.getWord();
        Double memoryStrength = wordState.getMemoryStrength();
        String currentState = wordState.getCurrentState();

        log.info("🔵 開始生成題目: word='{}', strength={:.2f}, state={}",
                word.getWordText(), memoryStrength, currentState);

        // ========== 步驟 1：根據記憶強度決定題型 ==========
        String questionType = determineQuestionType(memoryStrength);
        log.debug("題型決定: {} (strength={:.2f})", questionType, memoryStrength);

        // ========== 步驟 2：根據題型生成選項 ==========
        QuestionDTO question;

        switch (questionType) {
            case "EASY":
                question = generateEasyQuestion(wordState);
                break;
            case "NORMAL":
                question = generateNormalQuestion(wordState);
                break;
            case "HARD":
                question = generateHardQuestion(wordState);
                break;
            default:
                throw new RuntimeException("Unknown question type: " + questionType);
        }

        log.info("✅ 題目生成完成: type={}, word='{}', options={}",
                questionType, word.getWordText(),
                question.getOptions() != null ? question.getOptions().size() : 0);

        return question;
    }

    // ==================== 步驟 1：題型決定 ====================

    /**
     * 根據記憶強度決定題型（修復版本）
     *
     * 🔑 改進：不熟悉 → 簡單題，熟悉 → 困難題
     *
     * 新邏輯：
     * - memory_strength <= 0.2：EASY（英→中選擇）
     * - 0.2 < strength <= 0.5：NORMAL（中→英選擇）
     * - strength > 0.5：HARD（拼寫）
     */
    private String determineQuestionType(Double memoryStrength) {

        if (memoryStrength == null) {
            memoryStrength = 0.0;
        }

        if (memoryStrength <= 0.2) {
            log.trace("題型：EASY（strength={:.2f} <= 0.2，不熟悉）", memoryStrength);
            return "EASY";  // 簡單題幫助記憶

        } else if (memoryStrength <= 0.5) {
            log.trace("題型：NORMAL（0.2 < strength={:.2f} <= 0.5，中等熟悉）", memoryStrength);
            return "NORMAL";  // 普通題逐漸加強

        } else {
            log.trace("題型：HARD（strength={:.2f} > 0.5，熟悉）", memoryStrength);
            return "HARD";  // 困難題維持和加強
        }
    }

    // ==================== 步驟 2-1：生成簡單題（英→中選擇） ====================

    /**
     * 生成簡單題：英文 → 中文選擇
     *
     * 題目格式：
     * Question: apple
     * Options: [蘋果(✓), 橙子, 香蕉, 葡萄]
     *
     * 流程：
     * 1. 取得正確答案（中文翻譯）
     * 2. 生成 3 個干擾選項（同詞性）
     * 3. 隨機打亂選項
     * 4. 返回 QuestionDTO
     *
     * @param wordState 主題單字的狀態
     * @return 簡單題 DTO
     */
    private QuestionDTO generateEasyQuestion(WordState wordState) {

        Word word = wordState.getWord();
        String partOfSpeech = word.getPartOfSpeech();

        log.debug("🟢 生成簡單題: word='{}', pos={}", word.getWordText(), partOfSpeech);

        // 正確答案
        QuestionOptionDTO correctOption = QuestionOptionDTO.createOptionInternal(
                word.getId(),
                word.getTranslation(),
                true
        );

        // 生成干擾選項
        List<QuestionOptionDTO> distractors = generateDistractors(
                word,
                3,  // 需要 3 個干擾選項
                "chinese"  // 干擾選項顯示中文翻譯
        );

        log.debug("干擾選項生成: 數量={}", distractors.size());

        // 合併並隨機打亂
        List<QuestionOptionDTO> allOptions = new ArrayList<>();
        allOptions.add(correctOption);
        allOptions.addAll(distractors);

        Collections.shuffle(allOptions);

        // 找出打亂後的正確答案位置
        Long correctAnswerId = findCorrectAnswerId(allOptions);

        log.debug("選項打亂完成: 正確答案 ID={}", correctAnswerId);

        // 返回 QuestionDTO
        return QuestionDTO.createEasyQuestion(
                generateQuestionId(),
                word.getId(),
                word.getWordText(),
                wordState.getMemoryStrength(),
                wordState.getCurrentState(),
                allOptions,
                correctAnswerId
        );
    }

    // ==================== 步驟 2-2：生成普通題（中→英選擇） ====================

    /**
     * 生成普通題：中文 → 英文選擇
     *
     * 題目格式：
     * Question: 蘋果
     * Options: [apple(✓), orange, banana, grape]
     *
     * @param wordState 主題單字的狀態
     * @return 普通題 DTO
     */
    private QuestionDTO generateNormalQuestion(WordState wordState) {

        Word word = wordState.getWord();
        String partOfSpeech = word.getPartOfSpeech();

        log.debug("🟢 生成普通題: word='{}', pos={}", word.getWordText(), partOfSpeech);

        // 正確答案
        QuestionOptionDTO correctOption = QuestionOptionDTO.createOptionInternal(
                word.getId(),
                word.getWordText(),
                true
        );

        // 生成干擾選項
        List<QuestionOptionDTO> distractors = generateDistractors(
                word,
                3,  // 需要 3 個干擾選項
                "english"  // 干擾選項顯示英文單字
        );

        log.debug("干擾選項生成: 數量={}", distractors.size());

        // 合併並隨機打亂
        List<QuestionOptionDTO> allOptions = new ArrayList<>();
        allOptions.add(correctOption);
        allOptions.addAll(distractors);

        Collections.shuffle(allOptions);

        // 找出打亂後的正確答案位置
        Long correctAnswerId = findCorrectAnswerId(allOptions);

        log.debug("選項打亂完成: 正確答案 ID={}", correctAnswerId);

        // 返回 QuestionDTO
        return QuestionDTO.createNormalQuestion(
                generateQuestionId(),
                word.getId(),
                word.getTranslation(),
                wordState.getMemoryStrength(),
                wordState.getCurrentState(),
                allOptions,
                correctAnswerId
        );
    }

    // ==================== 步驟 2-3：生成困難題（中→英拼寫） ====================

    /**
     * 生成困難題：中文 → 英文拼寫
     *
     * 題目格式：
     * Question: 蘋果，請拼寫英文
     * 無選項，使用者需要輸入英文單字
     *
     * @param wordState 主題單字的狀態
     * @return 困難題 DTO
     */
    private QuestionDTO generateHardQuestion(WordState wordState) {

        Word word = wordState.getWord();

        log.debug("🟢 生成困難題: word='{}', translation='{}'",
                word.getWordText(), word.getTranslation());

        // 困難題無選項，直接返回
        QuestionDTO question = QuestionDTO.createHardQuestion(
                generateQuestionId(),
                word.getId(),
                word.getTranslation(),
                wordState.getMemoryStrength(),
                wordState.getCurrentState()
        );

        log.debug("困難題生成完成，無選項");

        return question;
    }

    // ==================== 核心邏輯：生成干擾選項 ====================

    /**
     * 生成干擾選項
     *
     * 策略：
     * 1. 查詢所有同詞性的單字
     * 2. 排除主題單字本身
     * 3. 排除難度差異過大的單字（建議 ±0.2）
     * 4. 隨機選擇 N 個
     *
     * 優化：如果可用單字不足，降級策略尋找備選
     *
     * @param mainWord 主題單字
     * @param count 需要的干擾選項數量（通常 3）
//     * @param language 選項語言（"english" 或 "chinese"）
     * @return 干擾選項列表
     */
    private List<QuestionOptionDTO> generateDistractors(
            Word mainWord,
            int count,
            String language) {

        log.debug("🟡 生成干擾選項: word='{}', count={}, language={}",
                mainWord.getWordText(), count, language);

        String partOfSpeech = mainWord.getPartOfSpeech();
        Double complexity = mainWord.getComplexityScore();

        // ========== 步驟 1：定義難度範圍 ==========
        Double complexityMin = complexity - 0.2;
        Double complexityMax = complexity + 0.2;

        log.trace("難度範圍: [{:.2f}, {:.2f}]", complexityMin, complexityMax);

        // ========== 步驟 2：查詢候選單字 ==========
        // 同詞性 + 難度相近 + 非主題單字
        List<Word> candidates = wordRepository.findCandidateDistractors(
                partOfSpeech,
                complexityMin,
                complexityMax,
                mainWord.getId(),
                PageRequest.of(0, count * 5)  // 查詢超額，以便選擇
        );

        log.debug("候選單字數量: {} (requested: {}, page size: {})",
                candidates.size(), count, count * 5);

        // ========== 步驟 3：降級策略 ==========
        if (candidates.size() < count) {
            log.warn("⚠️ 候選單字不足 ({}/{}), 嘗試降級策略", candidates.size(), count);

            // 降級 1：擴大難度範圍
            List<Word> backup1 = wordRepository.findCandidateDistractors(
                    partOfSpeech,
                    complexity - 0.4,
                    complexity + 0.4,
                    mainWord.getId(),
                    PageRequest.of(0, count * 5)
            );

            if (backup1.size() > candidates.size()) {
                candidates = backup1;
                log.debug("✅ 降級 1 成功: 擴大難度範圍，候選數 {}", candidates.size());
            }

            // 降級 2：移除難度限制
            if (candidates.size() < count) {
                List<Word> backup2 = wordRepository.findWordsByPartOfSpeech(
                        partOfSpeech,
                        mainWord.getId(),
                        PageRequest.of(0, count * 5)
                );

                if (backup2.size() > candidates.size()) {
                    candidates = backup2;
                    log.debug("✅ 降級 2 成功: 移除難度限制，候選數 {}", candidates.size());
                }
            }
        }

        // ========== 步驟 4：隨機選擇 N 個 ==========
        Collections.shuffle(candidates);

        List<QuestionOptionDTO> distractors = new ArrayList<>();

        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            Word distractor = candidates.get(i);
            String content = "english".equals(language)
                    ? distractor.getWordText()
                    : distractor.getTranslation();

            QuestionOptionDTO option = QuestionOptionDTO.createOptionInternal(
                    distractor.getId(),
                    content,
                    false  // 干擾選項不正確
            );

            distractors.add(option);

            log.trace("干擾選項 {}: {} (pos={}, complexity={:.2f})",
                    i + 1, distractor.getWordText(), distractor.getPartOfSpeech(),
                    distractor.getComplexityScore());
        }

        if (distractors.size() < count) {
            log.warn("⚠️ 最終干擾選項仍不足: {}/{}", distractors.size(), count);
        }

        log.debug("✅ 干擾選項生成完成: {} 個", distractors.size());

        return distractors;
    }

    // ==================== 工具方法 ====================

    /**
     * 找出打亂後的正確答案 ID
     *
     * @param options 打亂後的選項列表
     * @return 正確答案的 Word ID
     */
    private Long findCorrectAnswerId(List<QuestionOptionDTO> options) {
        return options.stream()
                .filter(opt -> Boolean.TRUE.equals(opt.getIsCorrect()))
                .map(QuestionOptionDTO::getId)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("❌ 找不到正確答案！");
                    return new RuntimeException("No correct answer found in options");
                });
    }

    /**
     * 生成題目 ID（用於追蹤）
     *
     * @return 唯一的題目 ID
     */
    private Long generateQuestionId() {
        return System.nanoTime();
    }
}