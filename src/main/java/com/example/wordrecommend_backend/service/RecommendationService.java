package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.WordDTO;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.entity.Word;
import com.example.wordrecommend_backend.entity.WordState;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;
    private final ReviewService reviewService; // 注入 ReviewService 以觸發事件

    @Transactional
    public List<WordDTO> getWordRecommendations(User user, int limit) {

        // --- 【核心修正】回到最初的「新/舊二分法」混合策略 ---

        // 1. 確定新舊單字比例與數量 (我們先硬編碼 30% 新字)
        double newWordRatio = 0.3;
        int numNew = (int) Math.round(limit * newWordRatio);
        int numReview = limit - numNew;

        // 2. 挑選需要複習的單字 (根據 P 值排序)
        List<WordState> reviewStates = wordStateRepository.findReviewWords(user, PageRequest.of(0, numReview));
        List<Word> reviewWords = reviewStates.stream()
                .map(WordState::getWord)
                .collect(Collectors.toList());

        // 3. 處理「複習字不足」的例外情況，用新字補足
        int actualReviewCount = reviewWords.size();
        int finalNumNew = limit - actualReviewCount;

        // 4. 挑選適合學習的新單字 (根據複雜度排序)
        List<Word> newWords = new ArrayList<>();
        if (finalNumNew > 0) {
            newWords = wordRepository.findNewWords(user, PageRequest.of(0, finalNumNew));
        }

        // 5. 合併成最終列表
        List<Word> finalList = new ArrayList<>();
        finalList.addAll(reviewWords);
        finalList.addAll(newWords);

        // 6. 【觸發閱讀事件】為列表中的每一個單字記錄一次「閱讀」事件
        if (!finalList.isEmpty()) {
            finalList.forEach(word -> reviewService.recordReadEvent(user, word));
        }

        // 7. 最終隨機打亂
        Collections.shuffle(finalList);

        return finalList.stream()
                .map(WordDTO::fromEntity) // 使用我們剛剛建立的轉換方法
                .collect(Collectors.toList());
    }
}