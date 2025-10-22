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
    public void recordReadEvent(User user, Word word) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 記錄閱讀歷史 (邏輯不變)
        saveReviewHistory(user, word, InteractionType.READ, 0, null, now);

        // 2. 執行「持續微增長」邏輯
        WordState currentState = wordStateRepository.findByUserAndWord(user, word)
                .orElseGet(() -> initializeNewState(user, word));

        // 【核心升級】無論當前是什麼狀態，每一次 READ 都會產生影響

        // a. 如果是第一次接觸 (S0)，進行「播種」
        if ("S0".equals(currentState.getCurrentState())) {
            currentState.setCurrentState("S1");
            currentState.setMemoryStrength(0.1); // 給予一個基礎印象分
        }
        // b. 如果已經有印象了 (S1, S2, S3)，進行「微增長」
        else {
            double currentStrength = currentState.getMemoryStrength();
            double passiveLearningFactor = 0.05; // 這可以是一個可配置的參數

            // 應用公式：新的強度 = 舊的強度 + 學習因子 * (1 - 舊的強度)
            double newStrength = currentStrength + passiveLearningFactor * (1 - currentStrength);

            // 確保強度不會超過 1.0
            currentState.setMemoryStrength(Math.min(1.0, newStrength));
        }

        // 無論是哪種情況，都更新最後互動時間
        currentState.setLastReviewTime(now);
        wordStateRepository.save(currentState);
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