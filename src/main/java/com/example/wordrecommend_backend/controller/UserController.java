package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.LoginRequest; // 下面會建立
import com.example.wordrecommend_backend.dto.AuthResponse; // 下面會建立
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.service.UserService;
import com.example.wordrecommend_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    // 這個 API 是受保護的，只有登入的使用者才能存取
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {
        // @AuthenticationPrincipal 是 Spring Security 的一個強大功能
        // 它可以直接將已認證的使用者物件注入到方法參數中
        return ResponseEntity.ok(currentUser);
    }

    // 我們將在後續步驟中加入 PUT /me 和 DELETE /me
}