package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.AuthResponse;
import com.example.wordrecommend_backend.dto.LoginRequest;
import com.example.wordrecommend_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public AuthResponse login(LoginRequest loginRequest) {
        // 1. 使用 Spring Security 的 AuthenticationManager 進行身份驗證
        //    如果帳號密碼錯誤，這裡會直接拋出異常
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        // 2. 驗證成功後，從資料庫讀取使用者詳細資訊
        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());

        // 3. 使用 JwtUtil 產生 JWT
        final String jwt = jwtUtil.generateToken(userDetails);

        // 4. 回傳包含 JWT 的 AuthResponse
        return new AuthResponse(jwt);
    }
}