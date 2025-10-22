package com.example.wordrecommend_backend.dto;

import com.example.wordrecommend_backend.entity.WordState;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WordStateDTO {

    private Long id;
    private Long userId;
    private Long wordId;
    private String wordText; // 為了方便前端顯示，我們可以加入單字本身
    private Double memoryStrength;
    private String currentState;
    private LocalDateTime lastReviewTime;
    private Double nextReviewPriority;

    // 一個方便的轉換方法
    public static WordStateDTO fromEntity(WordState entity) {
        WordStateDTO dto = new WordStateDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUser().getId()); // 在 Session 開啟時就先取得 ID
        dto.setWordId(entity.getWord().getId()); // 在 Session 開-啟時就先取得 ID
        dto.setWordText(entity.getWord().getWordText());
        dto.setMemoryStrength(entity.getMemoryStrength());
        dto.setCurrentState(entity.getCurrentState());
        dto.setLastReviewTime(entity.getLastReviewTime());
        dto.setNextReviewPriority(entity.getNextReviewPriority());
        return dto;
    }
}