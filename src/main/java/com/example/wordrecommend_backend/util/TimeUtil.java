package com.example.wordrecommend_backend.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

    // 每天的毫秒數
    private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;

    /**
     * 計算兩個時間點之間的日數差 (Delta t)。
     * @param startT 上次複習時間 (lastReviewTime)
     * @param endT 當前時間 (currentTime)
     * @return 兩個時間點間的日數差 (Double)
     */
    public static double calculateDaysDifference(LocalDateTime startT, LocalDateTime endT) {
        if (startT.isAfter(endT)) {
            // 避免負數時間差，如果開始時間晚於結束時間，返回 0
            return 0.0;
        }

        // 使用 ChronoUnit.MILLIS 計算毫秒差，然後轉換為天數
        long diffMillis = ChronoUnit.MILLIS.between(startT, endT);

        // 轉換為天數 (使用 double 保持精度)
        return (double) diffMillis / MILLIS_PER_DAY;
    }
}