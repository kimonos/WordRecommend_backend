package com.example.wordrecommend_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 統一由 Spring Security 的 CORS 濾器讀取這份設定。
 * 目的：允許前端 (http://localhost:5173) 帶著 HttpOnly Cookie 存取後端。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();

        // ❶ 允許的來源（請精準白名單；部署後加你的正式網域）
        // 這裡用 allowedOriginPatterns 是為了未來可支援子網域（若需要）
        c.setAllowedOriginPatterns(List.of(
//                "http://localhost:5173",      // 本機開發
                 "https://wordrecommend.me" // 上線網域（之後加）
        ));

        // ❷ 允許的方法（含 OPTIONS，讓 Preflight 能過）
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));

        // ❸ 允許的自訂標頭（前端常見）
        c.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-Token"
        ));

        // ❹ 允許夾帶憑證（Cookie）。若為 true，瀏覽器才會在跨站請求中帶上 HttpOnly Cookie
        c.setAllowCredentials(true);

        // ❺ 預檢快取秒數（減少瀏覽器頻繁發 OPTIONS）
        c.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c); // 套用於所有路徑
        return source;
    }
}
