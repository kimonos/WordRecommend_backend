package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.ReviewFeedbackRequest;
import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.dto.WordStateDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 複習控制器（v2.0）
 *
 * 端點：
 * - POST /reviews/feedback：提交答題結果
 * - GET  /reviews/words：獲取複習單字
 * - GET  /reviews/readiness：檢查複習準備狀態
 *
 * @author kimonos-test
 * @version 2.0
 * @since 2025-11-03
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 提交答題結果（v2.0）
     */
    @PostMapping("/feedback")
    public ResponseEntity<WordStateDTO> submitQuizAnswer(
            @AuthenticationPrincipal User user,
            @RequestBody ReviewFeedbackRequest request) {

        log.info("Received quiz feedback from user {}: wordId={}, selectedAnswer='{}', time={}ms",
                user.getId(), request.getWordId(), request.getSelectedAnswer(), request.getAnswerTimeMs());

        // 🔑 v2.1 修改：傳入 selectedAnswer，由後端判定
        WordState updatedState = reviewService.handleQuizAnswer(
                user,
                request.getWordId(),
                request.getSelectedAnswer(),  // ← 修改：傳送選擇的答案
                request.getAnswerTimeMs()
        );

        WordStateDTO dto = WordStateDTO.fromEntity(updatedState);

        log.info("Quiz feedback processed successfully for user {}: state={}, strength={:.3f}, " +
                        "totalCorrect={}, totalIncorrect={}",
                user.getId(), updatedState.getCurrentState(), updatedState.getMemoryStrength(),
                updatedState.getTotalCorrect(), updatedState.getTotalIncorrect());

        return ResponseEntity.ok(dto);
    }

    /**
     * 獲取複習單字列表（Phase 6.9）
     */
    @GetMapping("/words")
    public ResponseEntity<List<WordDTO>> getReviewWords(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("User {} requested {} review words", user.getId(), limit);

        List<WordDTO> reviewWords = reviewService.getReviewWords(user, limit);

        log.info("Returned {} review words for user {} (no S0 words)",
                reviewWords.size(), user.getId());

        return ResponseEntity.ok(reviewWords);
    }

    /**
     * 檢查複習準備狀態（Phase 6.8）
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> checkReviewReadiness(
            @AuthenticationPrincipal User user) {

        log.info("User {} checking review readiness", user.getId());

        Map<String, Object> readiness = reviewService.getReviewReadiness(user);

        return ResponseEntity.ok(readiness);
    }
}