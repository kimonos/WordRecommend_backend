package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Spring Security 登入時需要透過使用者名稱找到使用者
    Optional<User> findByUsername(String username);
}