package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/words")
    public ResponseEntity<List<WordDTO>> getWordRecommendations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        List<WordDTO> recommendedWords = recommendationService.getWordRecommendations(user, limit);
        return ResponseEntity.ok(recommendedWords);
    }
}