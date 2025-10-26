package com.example.wordrecommend_backend.dto;

import com.example.wordrecommend_backend.entity.Word;
import lombok.Data;

@Data
public class WordDTO {
    private Long id;
    private String wordText;
    private String translation;
    private String partOfSpeech;
    private String cefrLevel;
    private Double complexityScore;

    // 新增：可為 null，不影響其他 API
    private String state; // S0 | S1 | S2 | S3

    public static WordDTO fromEntity(Word entity) {
        if (entity == null) return null;
        WordDTO dto = new WordDTO();
        dto.setId(entity.getId());
        dto.setWordText(entity.getWordText());
        dto.setTranslation(entity.getTranslation());
        dto.setPartOfSpeech(entity.getPartOfSpeech());
        dto.setCefrLevel(entity.getCefrLevel());
        dto.setComplexityScore(entity.getComplexityScore());
        return dto;
    }

    // 新增：帶狀態的工廠方法（不影響原本用法）
    public static WordDTO fromEntityWithState(Word entity, String state) {
        WordDTO dto = fromEntity(entity);
        dto.setState(state);
        return dto;
    }
}