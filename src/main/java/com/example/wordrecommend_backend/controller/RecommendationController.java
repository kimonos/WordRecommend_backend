package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.ReadEventRequest;
import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.dto.WordStateDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.service.RecommendationService;
import com.example.wordrecommend_backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 推薦控制器（v2.0）
 *
 * 端點：
 * - GET  /recommendations/words：獲取推薦單字
 * - POST /recommendations/events/read：記錄閱讀事件
 * - GET  /recommendations/stats：獲取學習統計（可選）
 *
 * 改進：
 * - 閱讀事件整合 Phase 3 算法
 * - 返回更新後的 WordState（而非 Void）
 *
 * @author kimonos-test
 * @version 2.0
 * @since 2025-11-03
 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;
//    private final RecommendationService recommendationService;

    /**
     * 獲取推薦單字（v2.0 - Phase 5 完成）
     *
     * 端點：GET /recommendations/words?limit=20
     *
     * 回應格式：
     * [
     *   {
     *     "id": 123,
     *     "text": "apple",
     *     "meaning": "蘋果",
     *     "cefrLevel": "A1",
     *     "currentState": "S0",
     *     "complexityScore": 0.2
     *   },
     *   ...
     * ]
     *
     * 推薦策略：
     * - 新單字為主（60%）
     * - 學習閉環（35%）
     * - 遺忘提醒（5%）
     * - 根據新單字剩餘量動態調整
     *
     * @param user 當前使用者
     * @param limit 推薦數量（預設 10）
     * @return 推薦的單字列表
     */
    @GetMapping("/words")
    public ResponseEntity<List<WordDTO>> getWordRecommendations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("User {} requested {} word recommendations", user.getId(), limit);

        List<WordDTO> recommendedWords = recommendationService.getWordRecommendations(user, limit);

        log.info("Returned {} recommendations for user {}", recommendedWords.size(), user.getId());

        return ResponseEntity.ok(recommendedWords);
    }

    /**
     * 記錄閱讀事件（v2.0 - Phase 6 完成）
     *
     * 端點：POST /recommendations/events/read
     *
     * 請求格式：
     * {
     *   "wordId": 123,
     *   "durationMs": 5000
     * }
     *
     * 回應格式：
     * {
     *   "wordId": 123,
     *   "wordText": "apple",
     *   "memoryStrength": 0.05,
     *   "currentState": "S1",
     *   "readCount": 1,
     *   "avgReadDuration": 5.0
     * }
     *
     * 業務流程：
     * 1. 驗證使用者身份
     * 2. 轉換時長（毫秒 → 秒）
     * 3. 調用 ReviewService.handleReadingEvent()
     * 4. 返回更新後的 WordState
     *
     * 與答題的區別：
     * - 閱讀：被動學習，增益小（ΔM = 0.01 ~ 0.05）
     * - 答題：主動回憶，增益大（ΔM = 0.1 ~ 0.3）
     *
     * @param user 當前使用者
     * @param request 閱讀事件請求
     * @return 更新後的 WordState（DTO 格式）
     */
    @PostMapping("/events/read")
    public ResponseEntity<WordStateDTO> recordReadingEvent(
            @AuthenticationPrincipal User user,
            @RequestBody ReadEventRequest request) {

        log.info("User {} read word {}: duration={}ms",
                user.getId(), request.wordId(), request.durationMs());

        // 🔑 轉換時長：毫秒 → 秒
        double durationSeconds = request.durationMs() / 1000.0;

        // 🔑 調用新的 v2.0 方法
        WordState updatedState = recommendationService.handleReadingEvent(
                user,
                request.wordId(),
                durationSeconds
        );

        // 轉換為 DTO
        WordStateDTO dto = WordStateDTO.fromEntity(updatedState);

        log.info("Reading event processed for user {}: state={}, strength={:.3f}, read_count={}",
                user.getId(), updatedState.getCurrentState(), updatedState.getMemoryStrength(),
                updatedState.getReadCount());

        return ResponseEntity.ok(dto);
    }

    /**
     * 獲取學習統計（可選功能）
     *
     * 端點：GET /recommendations/stats
     *
     * 回應格式：
     * {
     *   "newWords": 1000,
     *   "forgottenWords": 5,
     *   "learningWords": 50,
     *   "reviewingWords": 30,
     *   "masteredWords": 20,
     *   "totalLearned": 105
     * }
     *
     * 用途：
     * - 首頁儀表板
     * - 學習報告
     *
     * @param user 當前使用者
     * @return 學習統計資料
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLearningStats(
            @AuthenticationPrincipal User user) {

        log.info("User {} requested learning stats", user.getId());

        Map<String, Object> stats = recommendationService.getLearningStatsSummary(user);

        log.debug("Learning stats for user {}: {}", user.getId(), stats);

        return ResponseEntity.ok(stats);
    }
    /**
     * 檢查複習準備狀態
     *
     * 端點：GET /recommendations/review-readiness
     *
     * 回應格式：
     * {
     *   "canReview": true,
     *   "totalReviewable": 15,
     *   "minRequired": 10,
     *   "remaining": 0,
     *   "breakdown": {
     *     "forgotten": 2,
     *     "learning": 8,
     *     "reviewing": 3,
     *     "mastered": 2
     *   },
     *   "suggestion": "你已經可以開始複習了！"
     * }
     *
     * @param user 當前使用者
     * @return 複習準備狀態
     */

}