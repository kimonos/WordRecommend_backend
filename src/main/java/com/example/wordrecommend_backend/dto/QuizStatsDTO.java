package com.example.wordrecommend_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 複習統計 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizStatsDTO {

    /**
     * 總題數
     */
    private Integer total;

    /**
     * 答對數
     */
    private Integer correct;

    /**
     * 答錯數
     */
    private Integer incorrect;

    /**
     * 正確率（百分比）
     */
    private Double accuracy;
}