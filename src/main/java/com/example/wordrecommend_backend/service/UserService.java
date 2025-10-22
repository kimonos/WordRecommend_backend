package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.dto.RegisterRequest;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(RegisterRequest registerRequest) {
        // 1. 檢查使用者名稱是否已被使用
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("錯誤：使用者名稱已被註冊！");
        }

        // 2. 檢查 Email 是否已被使用
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("錯誤：此 Email 已被註冊！");
        }

        // 3. 建立新的 User 物件
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());

        // 4. 將密碼加密後再設定
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // 5. 存入資料庫並回傳
        return userRepository.save(user);
    }
}