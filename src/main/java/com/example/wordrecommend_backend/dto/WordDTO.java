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

    // 一個方便的靜態工廠方法，用於從 Entity 轉換
    public static WordDTO fromEntity(Word entity) {
        if (entity == null) {
            return null;
        }
        WordDTO dto = new WordDTO();
        dto.setId(entity.getId());
        dto.setWordText(entity.getWordText());
        dto.setTranslation(entity.getTranslation());
        dto.setPartOfSpeech(entity.getPartOfSpeech());
        dto.setCefrLevel(entity.getCefrLevel());
        dto.setComplexityScore(entity.getComplexityScore());
        return dto;
    }
}