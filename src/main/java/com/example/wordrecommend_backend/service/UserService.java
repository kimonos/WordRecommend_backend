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
        // 1) 檢查暱稱是否已被使用（忽略大小寫）
        if (userRepository.existsByUsernameIgnoreCase(registerRequest.getUsername())) {
            throw new IllegalArgumentException("錯誤：使用者名稱已被註冊！");
        }
        // 2) 檢查 Email 是否已被使用（忽略大小寫）
        if (userRepository.existsByEmailIgnoreCase(registerRequest.getEmail())) {
            throw new IllegalArgumentException("錯誤：此 Email 已被註冊！");
        }

        // 3) 建立使用者
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // 4) 存入
        return userRepository.save(user);
    }
}