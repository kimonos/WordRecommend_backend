package com.example.wordrecommend_backend.dto;

import lombok.Data;

@Data
public class ReadingTimeRequest {
    private Long wordId;
    private long durationMs;
}