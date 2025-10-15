package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.service.StateUpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final StateUpdateService stateUpdateService;

    // 依賴注入 StateUpdateService
    public ReviewController(StateUpdateService stateUpdateService) {
        this.stateUpdateService = stateUpdateService;
    }

    /**
     * POST /api/review/feedback
     * 接收使用者答題回饋，觸發 StateUpdateService 執行演算法計算。
     * * @param wordId       單字的 ID (Long)
     * @param isCorrect    本次回答是否正確 (Boolean)
     * @param answerTimeMs 回答所花時間 (Long, 毫秒)
     * @return 更新後的 WordState 實體 (JSON)
     */
    @PostMapping("/feedback")
    public ResponseEntity<WordState> submitFeedback(
            @RequestParam Long wordId,
            @RequestParam Boolean isCorrect,
            @RequestParam Long answerTimeMs
    ) {
        // 核心：呼叫 Service 層來執行演算法邏輯
        WordState updatedState = stateUpdateService.processAnswerFeedback(
                wordId,
                isCorrect,
                answerTimeMs
        );

        // 返回更新後的狀態，供前端或測試人員驗證
        return ResponseEntity.ok(updatedState);
    }
}