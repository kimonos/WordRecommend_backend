package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.*;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.service.QuestionGenerationService;
import com.example.wordrecommend_backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 複習控制器（v3.2 - 支援會話管理，防止重複推薦）
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;
    private final QuestionGenerationService questionGenerationService;

    // 🔑 簡單的會話管理（生產環境應使用 Redis）
    private final Map<String, QuizSession> activeSessions = new HashMap<>();

    /**
     * 【新增】開始複習會話
     *
     * 端點：POST /reviews/start-quiz
     *
     * 🔑 流程：
     * 1. 一次性獲取所有題目所需的單字（不重複）
     * 2. 建立會話
     * 3. 返回會話 ID 和單字列表
     *
     * @param user 當前登入使用者
     * @param limit 複習題數
     * @return 會話信息
     */
    @PostMapping("/start-quiz")
    public ResponseEntity<QuizSessionDTO> startQuizSession(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("🔵 使用者 {} 開始複習會話 (limit={})", user.getId(), limit);

        try {
            if (user == null) {
                return ResponseEntity.badRequest().build();
            }

            // ========== 步驟 1：一次性獲取所有單字（不重複） ==========
            List<WordDTO> words = reviewService.getReviewWords(user, limit, new HashSet<>());

            if (words.isEmpty()) {
                log.warn("⚠️ 沒有可複習的單字");
                return ResponseEntity.ok(null);
            }

            // ========== 步驟 2：建立會話 ==========
            String sessionId = UUID.randomUUID().toString();

            QuizSession session = new QuizSession();
            session.setSessionId(sessionId);
            session.setUserId(user.getId());
            session.setWordIds(words.stream().map(WordDTO::getId).collect(Collectors.toList()));
            session.setCurrentIndex(0);
            session.setCreatedAt(System.currentTimeMillis());

            activeSessions.put(sessionId, session);

            log.info("✅ 複習會話已建立: sessionId={}, userId={}, totalWords={}",
                    sessionId, user.getId(), words.size());

            // ========== 步驟 3：返回會話信息 ==========
            QuizSessionDTO response = new QuizSessionDTO();
            response.setSessionId(sessionId);
            response.setTotalQuestions(words.size());
            response.setWordIds(words.stream().map(WordDTO::getId).collect(Collectors.toList()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 建立複習會話失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 獲取下一道題目
     *
     * 端點：GET /reviews/next-question
     *
     * 🔑 改進：
     * - 需要傳入 sessionId
     * - 從會話中獲取下一個單字 ID
     * - 確保不會重複推薦
     *
     * @param user 當前登入使用者
     * @param sessionId 會話 ID
     * @return 題目 DTO
     */
    @GetMapping("/next-question")
    public ResponseEntity<QuestionDTO> getNextQuestion(
            @AuthenticationPrincipal User user,
            @RequestParam String sessionId) {

        log.info("🔵 使用者 {} 請求下一道題目 (sessionId={})", user.getId(), sessionId);

        try {
            if (user == null) {
                return ResponseEntity.badRequest().build();
            }

            // ========== 步驟 1：驗證會話 ==========
            QuizSession session = activeSessions.get(sessionId);

            if (session == null) {
                log.error("❌ 會話不存在: sessionId={}", sessionId);
                return ResponseEntity.badRequest().build();
            }

            if (!session.getUserId().equals(user.getId())) {
                log.error("❌ 會話不屬於此使用者");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // ========== 步驟 2：檢查是否所有題目已完成 ==========
            if (session.getCurrentIndex() >= session.getWordIds().size()) {
                log.info("✅ 所有題目已完成: sessionId={}", sessionId);
                return ResponseEntity.ok(null);
            }

            // ========== 步驟 3：獲取當前題目的單字 ID ==========
            Long wordId = session.getWordIds().get(session.getCurrentIndex());

            log.debug("當前題目索引: {}/{}, wordId={}",
                    session.getCurrentIndex(), session.getWordIds().size(), wordId);

            // ========== 步驟 4：獲取單字狀態 ==========
            WordState wordState = reviewService.findWordStateByWordId(user, wordId);

            if (wordState == null) {
                log.error("❌ 找不到 WordState: wordId={}", wordId);
                throw new RuntimeException("WordState not found");
            }

            // ========== 步驟 5：生成題目 ==========
            QuestionDTO question = questionGenerationService.generateQuestion(wordState, null);

            // ========== 步驟 6：在回應中包含會話信息 ==========
            // （可選）便於前端追蹤進度
            log.info("✅ 題目生成成功: sessionId={}, wordId={}, type={}, progress={}/{}",
                    sessionId, wordId, question.getQuestionType(),
                    session.getCurrentIndex() + 1, session.getWordIds().size());

            return ResponseEntity.ok(question);

        } catch (Exception e) {
            log.error("❌ 生成題目失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 提交答題結果
     *
     * 端點：POST /reviews/submit-answer
     *
     * 🔑 改進：
     * - 接受 sessionId
     * - 答題後自動移進到下一題
     *
     * @param user 當前登入使用者
     * @param submission 答題提交
     * @param sessionId 會話 ID
     * @return 答題結果 DTO
     */
    @PostMapping("/submit-answer")
    public ResponseEntity<QuestionResultDTO> submitAnswer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AnswerSubmissionDTO submission,
            @RequestParam String sessionId) {

        log.info("🔵 使用者 {} 提交答題 (sessionId={})", user.getId(), sessionId);

        try {
            if (user == null) {
                return ResponseEntity.badRequest().build();
            }

            // ========== 步驟 1：驗證會話 ==========
            QuizSession session = activeSessions.get(sessionId);

            if (session == null) {
                log.error("❌ 會話不存在: sessionId={}", sessionId);
                return ResponseEntity.badRequest().build();
            }

            // ========== 步驟 2：完整委託給 Service 層 ==========
            WordState updatedState = reviewService.handleQuizAnswer(
                    user,
                    submission.getWordId(),
                    submission.getSelectedAnswer(),
                    submission.getAnswerTimeMs(),
                    submission.getQuestionType()
            );

            // ========== 步驟 3：驗證答案（用於建構結果） ==========
            boolean isCorrect = reviewService.validateAnswerByQuestionType(
                    updatedState.getWord(),
                    submission.getSelectedAnswer(),
                    submission.getQuestionType()
            );

            // ========== 步驟 4：推進到下一題 ==========
            session.setCurrentIndex(session.getCurrentIndex() + 1);

            log.debug("會話進度更新: {}/{}", session.getCurrentIndex(), session.getWordIds().size());

            // ========== 步驟 5：構建結果 ==========
            QuestionResultDTO result = buildQuestionResult(updatedState, submission, isCorrect);

            log.info("✅ 答題完成: sessionId={}, isCorrect={}, progress={}/{}",
                    sessionId, isCorrect,
                    session.getCurrentIndex(), session.getWordIds().size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ 答題失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 結束複習會話
     *
     * 端點：POST /reviews/end-quiz
     *
     * @param user 當前登入使用者
     * @param sessionId 會話 ID
     * @return 最終統計
     */
    @PostMapping("/end-quiz")
    public ResponseEntity<QuizStatsDTO> endQuizSession(
            @AuthenticationPrincipal User user,
            @RequestParam String sessionId) {

        log.info("🔵 使用者 {} 結束複習會話 (sessionId={})", user.getId(), sessionId);

        try {
            QuizSession session = activeSessions.get(sessionId);

            if (session == null) {
                return ResponseEntity.badRequest().build();
            }

            // 移除會話
            activeSessions.remove(sessionId);

            log.info("✅ 會話已結束: sessionId={}", sessionId);

            return ResponseEntity.ok(new QuizStatsDTO());

        } catch (Exception e) {
            log.error("❌ 結束會話失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 獲取複習單字
     */
    @GetMapping("/words")
    public ResponseEntity<List<WordDTO>> getReviewWords(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            List<WordDTO> words = reviewService.getReviewWords(user, limit);
            return ResponseEntity.ok(words);
        } catch (Exception e) {
            log.error("❌ 獲取複習單字失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 檢查複習準備狀態
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> checkReviewReadiness(
            @AuthenticationPrincipal User user) {

        try {
            Map<String, Object> readiness = reviewService.getReviewReadiness(user);
            return ResponseEntity.ok(readiness);
        } catch (Exception e) {
            log.error("❌ 檢查複習狀態失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 舊方法：提交答題結果（保持相容性）
     */
    @PostMapping("/feedback")
    public ResponseEntity<WordStateDTO> submitQuizAnswer(
            @AuthenticationPrincipal User user,
            @RequestBody ReviewFeedbackRequest request) {

        try {
            WordState updatedState = reviewService.handleQuizAnswer(
                    user,
                    request.getWordId(),
                    request.getSelectedAnswer(),
                    request.getAnswerTimeMs()
            );

            return ResponseEntity.ok(WordStateDTO.fromEntity(updatedState));

        } catch (Exception e) {
            log.error("❌ 答題失敗: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 私有方法 ====================

    private QuestionResultDTO buildQuestionResult(
            WordState updatedState,
            AnswerSubmissionDTO submission,
            boolean isCorrect) {

        String displayAnswer = getCorrectAnswerDisplay(
                submission.getQuestionType(),
                updatedState.getWord()
        );

        if (isCorrect) {
            return QuestionResultDTO.createCorrectResult(
                    submission.getSelectedAnswer(),
                    displayAnswer,
                    updatedState.getMemoryStrength(),
                    updatedState.getMemoryStrength(),
                    updatedState.getCurrentState(),
                    false,
                    updatedState.getNextReviewPriority()
            );
        } else {
            return QuestionResultDTO.createIncorrectResult(
                    submission.getSelectedAnswer(),
                    displayAnswer,
                    updatedState.getMemoryStrength(),
                    updatedState.getMemoryStrength(),
                    updatedState.getCurrentState(),
                    false,
                    false,
                    updatedState.getNextReviewPriority()
            );
        }
    }

    private String getCorrectAnswerDisplay(String questionType, com.example.wordrecommend_backend.entity.Word word) {
        switch (questionType) {
            case "EASY":
            case "NORMAL":
                return word.getTranslation();
            case "HARD":
                return word.getWordText();
            default:
                return word.getTranslation();
        }
    }

    // ==================== 內部類別 ====================

    /**
     * 複習會話（簡化版）
     *
     * 生產環境應使用 Redis 存儲會話
     */
    private static class QuizSession {
        private String sessionId;
        private Long userId;
        private List<Long> wordIds;
        private Integer currentIndex;
        private Long createdAt;

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public List<Long> getWordIds() { return wordIds; }
        public void setWordIds(List<Long> wordIds) { this.wordIds = wordIds; }

        public Integer getCurrentIndex() { return currentIndex; }
        public void setCurrentIndex(Integer currentIndex) { this.currentIndex = currentIndex; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}