package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.ReadingTimeRequest;
import com.example.wordrecommend_backend.dto.ReviewFeedbackRequest;
import com.example.wordrecommend_backend.dto.WordStateDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.service.ReviewService;
//import com.example.wordrecommend_backend.service.StateUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews") // API 路徑設為 /reviews
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/feedback")
    public ResponseEntity<WordStateDTO> processQuizFeedback(
            @AuthenticationPrincipal User user,
            @RequestBody ReviewFeedbackRequest request) {
        WordState updatedEntity = reviewService.processQuizFeedback(user, request);
        WordStateDTO responseDto = WordStateDTO.fromEntity(updatedEntity);
        return ResponseEntity.ok(responseDto);
    }

//    @PostMapping("/reading_time")
//    public ResponseEntity<Void> processReadingTime(
//            @AuthenticationPrincipal User user,
//            @RequestBody ReadingTimeRequest request) {
//
//        reviewService.processReadingTime(user, request);
//        return ResponseEntity.ok().build();
//    }
}