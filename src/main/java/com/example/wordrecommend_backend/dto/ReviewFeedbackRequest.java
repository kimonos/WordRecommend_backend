package com.example.wordrecommend_backend.dto;

import lombok.Data;

@Data
public class ReviewFeedbackRequest {
    private Long wordId;
    private boolean isCorrect;
    private long answerTimeMs;
}