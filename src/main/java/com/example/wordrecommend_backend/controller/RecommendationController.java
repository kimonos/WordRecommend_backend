package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.ReadEventRequest;
import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.service.RecommendationService;
import com.example.wordrecommend_backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ReviewService reviewService;
    private final WordRepository wordRepository;

    @GetMapping("/words")
    public ResponseEntity<List<WordDTO>> getWordRecommendations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        List<WordDTO> recommendedWords = recommendationService.getWordRecommendations(user, limit);
        return ResponseEntity.ok(recommendedWords);
    }

    @PostMapping("/events/read")
    public ResponseEntity<Void> recordRead(
            @AuthenticationPrincipal User user,
            @RequestBody ReadEventRequest request
    ) {
        Word word = wordRepository.findById(request.wordId())
                .orElseThrow(() -> new RuntimeException("Word not found: " + request.wordId()));

        // 建議在 ReviewService 增加一個能接收 durationMs 的 overload
        reviewService.recordReadEvent(user, word, request.durationMs());

        return ResponseEntity.ok().build();
    }
}