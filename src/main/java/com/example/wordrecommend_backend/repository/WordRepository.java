package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<[Entity 類名], [ID 類型]>
public interface WordRepository extends JpaRepository<Word, Long> {
    // Spring Data JPA 會自動提供 findAll(), save(), findById() 等方法
}