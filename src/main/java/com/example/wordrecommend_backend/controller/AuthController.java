package com.example.wordrecommend_backend.controller;

import com.example.wordrecommend_backend.dto.AuthResponse;
import com.example.wordrecommend_backend.dto.LoginRequest;
import com.example.wordrecommend_backend.dto.RegisterRequest;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.service.AuthService;
import com.example.wordrecommend_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth") // API 路徑設為 /auth
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        try {
            User registeredUser = userService.register(registerRequest);
            // 為了安全，回傳的 User 物件不應該包含密碼，我們後續可以再優化
            return ResponseEntity.ok(registeredUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.login(loginRequest);
        return ResponseEntity.ok(authResponse);
    }
}