package com.example.wordrecommend_backend.config;

import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.repository.UserRepository;
//import com.example.wordrecommend_backend.service.CustomOAuth2UserService;
import com.example.wordrecommend_backend.service.RefreshTokenService;
import com.example.wordrecommend_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;                        // 你現有的工具
    private final UserRepository userRepository;          // 用來確認本地使用者已 upsert
    private final CustomUserDetailsConfig customUserDetailsConfig;  // ★ 新增：載入 UserDetails
    private final RefreshTokenService refreshTokenService;

    @Value("${app.security.jwt.cookie-name:APP_TOKEN}")
    private String cookieName;

    @Value("${app.security.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException {
        OAuth2User principal = (OAuth2User) auth.getPrincipal();
        String email = (String) principal.getAttributes().get("email");

        // 保險：確保剛剛在 CustomOAuth2UserService 已建立/更新本地使用者
//        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
//
//        // ★ 用 UserDetails 來產生 JWT（符合你現在的簽名）
//        UserDetails userDetails = customUserDetailsConfig.loadUserByUsername(email);
//        String token = jwtUtil.generateToken(userDetails);
        refreshTokenService.issueNewFamilyTokensForEmail(email, req, res);

        res.sendRedirect(frontendUrl);
    }
}
//package com.example.wordrecommend_backend.config;
//
//import com.example.wordrecommend_backend.entity.User;
//import com.example.wordrecommend_backend.repository.UserRepository;
//import com.example.wordrecommend_backend.util.JwtUtil;
//import jakarta.servlet.http.Cookie;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
//
//    private final JwtUtil jwtUtil;
//    private final UserRepository userRepository;
//    private final CustomUserDetailsConfig customUserDetailsConfig;
//
//    @Value("${app.security.jwt.cookie-name:APP_TOKEN}")
//    private String cookieName;
//
//    @Value("${app.security.frontend-url:http://localhost:5173}")
//    private String frontendUrl;
//
//    @Override
//    public void onAuthenticationSuccess(HttpServletRequest req,
//                                        HttpServletResponse res,
//                                        Authentication auth) throws IOException {
//
//        log.info("========================================");
//        log.info("===== OAuth2 認證成功 Handler 開始 =====");
//        log.info("========================================");
//
//        try {
//            OAuth2User principal = (OAuth2User) auth.getPrincipal();
//            log.info("✅ 取得 OAuth2User principal");
//            log.info("   Principal name: {}", principal.getName());
//            log.info("   Principal attributes: {}", principal.getAttributes());
//
//            String email = (String) principal.getAttributes().get("email");
//            log.info("📧 從 attributes 取得 email: {}", email);
//
//            if (email == null || email.isBlank()) {
//                log.error("❌ Email 為空或空白!");
//                log.error("   可用的 attributes keys: {}", principal.getAttributes().keySet());
//                throw new RuntimeException("Email is null or blank from OAuth2 attributes");
//            }
//
//            // 保險：確保剛剛在 CustomOAuth2UserService 已建立/更新本地使用者
//            log.info("🔍 開始從資料庫查找使用者...");
//            log.info("   查詢 email: {}", email);
//
//            var userOptional = userRepository.findByEmailIgnoreCase(email);
//            log.info("   查詢結果: {}", userOptional.isPresent() ? "找到使用者" : "未找到使用者");
//
//            if (userOptional.isEmpty()) {
//                log.error("❌ 資料庫中找不到使用者!");
//                log.error("   Email: {}", email);
//                log.error("   這表示 CustomOAuth2UserService 可能沒有成功儲存使用者");
//
//                // 列出資料庫中所有使用者 (debug 用)
//                log.error("   資料庫中現有的使用者:");
//                userRepository.findAll().forEach(u ->
//                        log.error("     - ID: {}, Email: {}, Username: {}", u.getId(), u.getEmail(), u.getUsername())
//                );
//
//                throw new RuntimeException("User not found in database: " + email);
//            }
//
//            User user = userOptional.get();
//            log.info("✅ 找到使用者!");
//            log.info("   User ID: {}", user.getId());
//            log.info("   Username: {}", user.getUsername());
//            log.info("   Email: {}", user.getEmail());
//            log.info("   Provider: {}", user.getProvider());
//            log.info("   ProviderId: {}", user.getProviderId());
//
//            // 用 UserDetails 來產生 JWT
//            log.info("🔑 開始載入 UserDetails...");
//            log.info("   載入的 username/email: {}", email);
//
//            UserDetails userDetails;
//            try {
//                userDetails = customUserDetailsConfig.loadUserByUsername(email);
//                log.info("✅ UserDetails 載入成功");
//                log.info("   UserDetails username: {}", userDetails.getUsername());
//                log.info("   UserDetails authorities: {}", userDetails.getAuthorities());
//            } catch (Exception e) {
//                log.error("❌ 載入 UserDetails 失敗!", e);
//                log.error("   錯誤訊息: {}", e.getMessage());
//                throw e;
//            }
//
//            log.info("🎫 開始生成 JWT Token...");
//            String token;
//            try {
//                token = jwtUtil.generateToken(userDetails);
//                log.info("✅ JWT Token 生成成功");
//                log.info("   Token 長度: {}", token.length());
//                log.info("   Token 前 20 字元: {}...", token.substring(0, Math.min(20, token.length())));
//            } catch (Exception e) {
//                log.error("❌ 生成 JWT Token 失敗!", e);
//                log.error("   錯誤訊息: {}", e.getMessage());
//                throw e;
//            }
//
//            log.info("🍪 建立 Cookie...");
//            log.info("   Cookie name: {}", cookieName);
//            log.info("   Cookie path: /");
//            log.info("   Cookie maxAge: {} 秒 (7天)", 7 * 24 * 60 * 60);
//
//            Cookie cookie = new Cookie(cookieName, token);
//            cookie.setHttpOnly(true);
//            cookie.setPath("/");
//            cookie.setMaxAge(7 * 24 * 60 * 60);
//            res.addCookie(cookie);
//            log.info("✅ Cookie 已加入 response");
//
//            log.info("🔀 準備重導向...");
//            log.info("   目標 URL: {}", frontendUrl);
//            res.sendRedirect(frontendUrl);
//
//            log.info("========================================");
//            log.info("===== OAuth2 認證成功 Handler 完成 =====");
//            log.info("========================================");
//
//        } catch (Exception e) {
//            log.error("========================================");
//            log.error("===== OAuth2 認證處理發生錯誤 =====");
//            log.error("========================================");
//            log.error("❌ 錯誤類型: {}", e.getClass().getName());
//            log.error("❌ 錯誤訊息: {}", e.getMessage());
//            log.error("❌ Stack trace:", e);
//            log.error("========================================");
//            throw e;
//        }
//    }
//}