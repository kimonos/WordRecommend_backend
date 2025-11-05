package com.example.wordrecommend_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定時任務配置
 *
 * 功能：
 * - 啟用 Spring 定時任務（@Scheduled）
 *
 * 用途：
 * - 定時清理過期的密碼重置 Token
 * - 其他定時任務（例如：清理過期的 Refresh Token）
 *
 * 注意：
 * - @EnableScheduling 必須加在配置類上
 * - 定時任務會在獨立的線程池中執行
 * - 預設線程池大小：1（可在 application.properties 中配置）
 *
 * 配置參數（application.properties）：
 * spring.task.scheduling.pool.size=2
 * spring.task.scheduling.thread-name-prefix=scheduled-task-
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 使用 Spring Boot 自動配置的定時任務執行器
     *
     * 如果需要自定義執行器，可以在此類中定義 @Bean
     *
     * 範例：
     * @Bean
     * public TaskScheduler taskScheduler() {
     *     ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
     *     scheduler.setPoolSize(5);
     *     scheduler.setThreadNamePrefix("scheduled-task-");
     *     scheduler.initialize();
     *     return scheduler;
     * }
     */
}