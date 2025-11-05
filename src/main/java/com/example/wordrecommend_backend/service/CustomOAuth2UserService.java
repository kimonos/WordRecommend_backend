//package com.example.wordrecommend_backend.service;
//
//import com.example.wordrecommend_backend.entity.AuthProvider;
//import com.example.wordrecommend_backend.entity.User;
//import com.example.wordrecommend_backend.oauth.OAuth2UserInfo;
//import com.example.wordrecommend_backend.oauth.OAuth2UserInfoFactory;
//import com.example.wordrecommend_backend.repository.UserRepository;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
//import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
//import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.stereotype.Service;
//
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//
//@Service
//@RequiredArgsConstructor
//public class CustomOAuth2UserService extends DefaultOAuth2UserService {
//
//    private final UserRepository userRepository;
//
//    @Override
//    @Transactional
//    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
//        OAuth2User delegate = super.loadUser(req);
//
//        String registrationId = req.getClientRegistration().getRegistrationId();
//        OAuth2UserInfo info = OAuth2UserInfoFactory.from(registrationId, delegate.getAttributes());
//
//        String email = info.getEmail();
//        // GitHub 可能沒有 email，先用 noreply 方案跑 MVP
//        if (email == null || email.isBlank()) {
//            if ("github".equalsIgnoreCase(registrationId)) {
//                Object login = delegate.getAttributes().get("login");
//                if (login != null) {
//                    email = login.toString() + "@users.noreply.github.com";
//                }
//            }
//        }
//        if (email == null || email.isBlank()) {
//            throw new OAuth2AuthenticationException("No email from OAuth2 provider");
//        }
//
//        AuthProvider provider = "google".equalsIgnoreCase(registrationId) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;
//
//        String finalEmail = email;
//        System.out.println("finalEmail: " + finalEmail);
//        User user = userRepository.findByEmailIgnoreCase(finalEmail)
//                .map(u -> {
//                    u.setUsername(defaultIfBlank(info.getName(), u.getUsername()));
//                    u.setAvatarUrl(defaultIfBlank(info.getImageUrl(), u.getAvatarUrl()));
//                    u.setProvider(provider);
//                    u.setProviderId(info.getId());
//                    Boolean verified = info.getEmailVerified();
//                    if (verified != null) u.setEmailVerified(verified);
//                    return u;
//                })
//                .orElseGet(() -> {
//                    User u = new User();
//                    u.setEmail(finalEmail);
//                    u.setUsername(defaultIfBlank(info.getName(), usernameFromEmail(finalEmail)));
//                    u.setAvatarUrl(info.getImageUrl());
//                    u.setProvider(provider);
//                    u.setProviderId(info.getId());
//                    Boolean verified = info.getEmailVerified();
//                    if (verified != null) u.setEmailVerified(verified);
//                    return u;
//                });
//
//        userRepository.save(user);
//
//        // 你的 schema 沒有角色欄位；先用固定 ROLE_USER
//        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
//
//        Map<String,Object> attrs = new HashMap<>(delegate.getAttributes());
//        attrs.put("localUserId", user.getId());
//        attrs.put("email", finalEmail); // 確保後面 success handler 拿得到
//
//        return new DefaultOAuth2User(authorities, attrs, "email");
//    }
//
//    private static String defaultIfBlank(String s, String fallback){
//        return (s == null || s.isBlank()) ? fallback : s;
//    }
//    private static String usernameFromEmail(String email){
//        int i = email.indexOf('@');
//        return i > 0 ? email.substring(0, i) : email;
//    }
//}
package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.entity.AuthProvider;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.oauth.OAuth2UserInfo;
import com.example.wordrecommend_backend.oauth.OAuth2UserInfoFactory;
import com.example.wordrecommend_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        // 強制印出,確保一定看得到
        System.out.println("\n\n========================================");
        System.out.println("!!! CustomOAuth2UserService.loadUser() 開始執行 !!!");
        System.out.println("========================================\n\n");

        log.info("===== OAuth2 登入流程開始 =====");

        OAuth2User delegate = super.loadUser(req);
        log.info("OAuth2User delegate 取得成功");
        log.info("Delegate attributes: {}", delegate.getAttributes());

        String registrationId = req.getClientRegistration().getRegistrationId();
        log.info("Registration ID: {}", registrationId);

        OAuth2UserInfo info = OAuth2UserInfoFactory.from(registrationId, delegate.getAttributes());
        log.info("OAuth2UserInfo 建立成功");
        log.info("User Info - ID: {}, Name: {}, Email: {}, Avatar: {}",
                info.getId(), info.getName(), info.getEmail(), info.getImageUrl());

        String email = info.getEmail();
        log.info("原始 email: {}", email);

        // GitHub 可能沒有 email，先用 noreply 方案跑 MVP
        if (email == null || email.isBlank()) {
            log.warn("Email 為空，嘗試從 GitHub login 取得");
            if ("github".equalsIgnoreCase(registrationId)) {
                Object login = delegate.getAttributes().get("login");
                log.info("GitHub login: {}", login);
                if (login != null) {
                    email = login.toString() + "@users.noreply.github.com";
                    log.info("使用 GitHub noreply email: {}", email);
                }
            }
        }

        if (email == null || email.isBlank()) {
            log.error("無法取得 email，拋出異常");
            throw new OAuth2AuthenticationException("No email from OAuth2 provider");
        }

        AuthProvider provider = "google".equalsIgnoreCase(registrationId) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;
        log.info("Provider: {}", provider);

        String finalEmail = email;
        log.info("最終 email: {}", finalEmail);
        System.out.println("最終 email: " + finalEmail);

        // 查找資料庫
        log.info("開始查找資料庫中是否存在使用者: {}", finalEmail);
        System.out.println("開始查找資料庫: " + finalEmail);

        var existingUser = userRepository.findByEmailIgnoreCase(finalEmail);
        log.info("資料庫查詢結果: {}", existingUser.isPresent() ? "找到現有使用者" : "未找到，將建立新使用者");
        System.out.println("查詢結果: " + (existingUser.isPresent() ? "找到" : "未找到"));

        User user = existingUser
                .map(u -> {
                    log.info("更新現有使用者 ID: {}", u.getId());
                    System.out.println("更新使用者 ID: " + u.getId());

                    u.setUsername(defaultIfBlank(info.getName(), u.getUsername()));
                    u.setAvatarUrl(defaultIfBlank(info.getImageUrl(), u.getAvatarUrl()));
                    u.setProvider(provider);
                    u.setProviderId(info.getId());
                    Boolean verified = info.getEmailVerified();
                    if (verified != null) u.setEmailVerified(verified);

                    return u;
                })
                .orElseGet(() -> {
                    log.info("建立新使用者");
                    System.out.println("建立新使用者: " + finalEmail);

                    User u = new User();
                    u.setEmail(finalEmail);
                    u.setUsername(defaultIfBlank(info.getName(), usernameFromEmail(finalEmail)));
                    u.setAvatarUrl(info.getImageUrl());
                    u.setProvider(provider);
                    u.setProviderId(info.getId());
                    Boolean verified = info.getEmailVerified();
                    if (verified != null) u.setEmailVerified(verified);

                    log.info("  新使用者 email: {}", u.getEmail());
                    log.info("  新使用者 username: {}", u.getUsername());

                    return u;
                });

        log.info("準備儲存使用者到資料庫");
        System.out.println("準備儲存使用者...");

        try {
            User savedUser = userRepository.save(user);
            log.info("✅ 使用者儲存成功! ID: {}", savedUser.getId());
            System.out.println("✅ 儲存成功! ID: " + savedUser.getId());
            System.out.println("   Email: " + savedUser.getEmail());
            System.out.println("   Username: " + savedUser.getUsername());
        } catch (Exception e) {
            log.error("❌ 儲存使用者失敗!", e);
            System.err.println("❌ 儲存失敗: " + e.getMessage());
            throw e;
        }

        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        Map<String,Object> attrs = new HashMap<>(delegate.getAttributes());
        attrs.put("localUserId", user.getId());
        attrs.put("email", finalEmail);

        System.out.println("\n========================================");
        System.out.println("!!! CustomOAuth2UserService.loadUser() 執行完成 !!!");
        System.out.println("========================================\n\n");

        log.info("===== OAuth2 登入流程完成 =====");
        return new DefaultOAuth2User(authorities, attrs, "email");
    }

    private static String defaultIfBlank(String s, String fallback){
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String usernameFromEmail(String email){
        int i = email.indexOf('@');
        return i > 0 ? email.substring(0, i) : email;
    }
}