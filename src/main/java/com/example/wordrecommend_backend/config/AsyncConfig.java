package com.example.wordrecommend_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 異步任務配置
 *
 * 用途：
 * - 啟用 @Async 註解支援
 * - 郵件發送在獨立線程中執行
 * - 不阻塞主線程，提升響應速度
 *
 * 效果：
 * - 使用者請求密碼重置後，立即返回響應
 * - 郵件發送在背景執行
 *
 * @author kimonos-test
 * @version 1.0
 * @since 2025-11-05
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 使用 Spring Boot 自動配置的異步執行器
    // 配置在 application.properties 中：
    // spring.task.execution.pool.core-size=2
    // spring.task.execution.pool.max-size=5
}