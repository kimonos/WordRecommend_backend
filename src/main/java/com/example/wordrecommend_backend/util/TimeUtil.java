package com.example.wordrecommend_backend.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

    // 每天的毫秒數
    private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;

    /**
     * 計算兩個時間點之間的日數差 (Delta t)
     *
     * 用途：
     * - 遺忘曲線計算（需要精確的時間差）
     * - 記憶衰減計算
     *
     * 特點：
     * - 返回浮點數，支援小數天數（如 2.5 天 = 60 小時）
     * - 更精確，適合數學計算
     *
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

    /**
     * 計算兩個時間點之間的日數差 (整數版本)
     *
     * 用途：
     * - S-1 狀態加成計算（需要整數天數判斷：<= 3 天, <= 7 天）
     * - 統計報告（例如：「你已經學習 30 天了」）
     * - UI 顯示（整數更直觀）
     *
     * 特點：
     * - 返回整數，向下取整（2.9 天 → 2 天）
     * - 更直觀，適合條件判斷
     *
     * 與 calculateDaysDifference() 的區別：
     * - calculateDaysDifference(): 返回 double（精確，如 2.5 天）
     * - calculateDaysDifferenceAsLong(): 返回 long（簡化，如 2 天）
     *
     * @param start 起始時間
     * @param end 結束時間
     * @return 天數差異（整數，向下取整）
     */
    public static long calculateDaysDifferenceAsLong(LocalDateTime start, LocalDateTime end) {
        if (start.isAfter(end)) {
            // 避免負數時間差
            return 0L;
        }

        // 使用 ChronoUnit.DAYS 直接計算天數（自動向下取整）
        return ChronoUnit.DAYS.between(start, end);
    }
}