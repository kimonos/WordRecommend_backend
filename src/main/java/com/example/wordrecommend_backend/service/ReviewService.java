package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.ReadingTimeRequest;
import com.example.wordrecommend_backend.dto.ReviewFeedbackRequest;
import com.example.wordrecommend_backend.entity.*;
import com.example.wordrecommend_backend.repository.ReviewHistoryRepository;
import com.example.wordrecommend_backend.repository.WordRepository;
import com.example.wordrecommend_backend.repository.WordStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final WordRepository wordRepository;
    private final WordStateRepository wordStateRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final AlgorithmCoreService algorithmCoreService;

    @Transactional
    public WordState processQuizFeedback(User user, ReviewFeedbackRequest request) {
        Word word = wordRepository.findById(request.getWordId())
                .orElseThrow(() -> new RuntimeException("找不到 ID 為 " + request.getWordId() + " 的單字"));

        WordState currentState = wordStateRepository.findByUserAndWord(user, word)
                .orElseGet(() -> initializeNewState(user, word));

        LocalDateTime now = LocalDateTime.now();

        double newMemoryStrength = algorithmCoreService.calculateNewMemoryStrength(
                currentState, word, request.isCorrect(), request.getAnswerTimeMs(), now
        );
        String newFsmState = algorithmCoreService.determineFsmState(newMemoryStrength);
        double newPriority = algorithmCoreService.calculateReviewPriority(
                newMemoryStrength, currentState.getLastReviewTime(), now, word.getComplexityScore()
        );

        currentState.setMemoryStrength(newMemoryStrength);
        currentState.setCurrentState(newFsmState);
        currentState.setLastReviewTime(now);
        currentState.setNextReviewPriority(newPriority);

        // 【核心修正】使用 InteractionType.QUIZ Enum，而不是 "QUIZ" 字串
        saveReviewHistory(user, word, InteractionType.QUIZ, request.getAnswerTimeMs(), request.isCorrect(), now);

        return wordStateRepository.save(currentState);
    }

//    @Transactional
//    public void processReadingTime(User user, ReadingTimeRequest request) {
//        Word word = wordRepository.findById(request.getWordId())
//                .orElseThrow(() -> new RuntimeException("找不到 ID 為 " + request.getWordId() + " 的單字"));
//
//        // 【核心修正】使用 InteractionType.READ Enum，而不是 "READ" 字串
//        saveReviewHistory(user, word, InteractionType.READ, request.getDurationMs(), null, LocalDateTime.now());
//    }
@Transactional
public void recordReadEvent(User user, Word word, long durationMs) {
    LocalDateTime now = LocalDateTime.now();

    saveReviewHistory(user, word, InteractionType.READ, 0, null, now);

    WordState state = wordStateRepository.findByUserAndWord(user, word)
            .orElseGet(() -> initializeNewState(user, word));

    // 更新閱讀累計/平均（若你有這些欄位）
    int cnt = (state.getReadCount() == null ? 0 : state.getReadCount()) + 1;
    double total = (state.getTotalReadDuration() == null ? 0.0 : state.getTotalReadDuration()) + durationMs / 1000.0;
    state.setReadCount(cnt);
    state.setTotalReadDuration(total);
    state.setAvgReadDuration(total / cnt);

    // 微增長（保持你原本的邏輯）
    if ("S0".equals(state.getCurrentState())) {
        state.setCurrentState("S1");
        state.setMemoryStrength(0.1);
    } else {
        double current = state.getMemoryStrength() == null ? 0.0 : state.getMemoryStrength();
        double passive = 0.05;
        double newStrength = Math.min(1.0, current + passive * (1 - current));
        state.setMemoryStrength(newStrength);
    }

    // 記錄最後閱讀時間
    state.setLastReadTime(now);
    state.setLastReviewTime(now);

    wordStateRepository.save(state);
}


    private WordState initializeNewState(User user, Word word) {
        WordState newState = new WordState();
        newState.setUser(user);
        newState.setWord(word);
        newState.setMemoryStrength(0.0);
        newState.setCurrentState("S0");
        newState.setLastReviewTime(LocalDateTime.now());
        newState.setNextReviewPriority(100.0);
        return newState;
    }

    // 【核心修正】將 'type' 參數的類型從 String 改為 InteractionType
    private void saveReviewHistory(User user, Word word, InteractionType type, long durationMs, Boolean isCorrect, LocalDateTime reviewTime) {
        ReviewHistory history = new ReviewHistory();
        history.setUser(user);
        history.setWord(word);
        history.setInteractionType(type); // 現在傳入的是 Enum 物件
        history.setDurationMs(durationMs);
        history.setIsCorrect(isCorrect);
        history.setReviewTime(reviewTime);
        reviewHistoryRepository.save(history);
    }
}