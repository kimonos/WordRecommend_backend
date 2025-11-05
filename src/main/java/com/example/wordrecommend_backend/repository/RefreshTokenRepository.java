package com.example.wordrecommend_backend.repository;

import com.example.wordrecommend_backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJtiHash(String jtiHash);

    @Query("""
           select rt from RefreshToken rt
           where rt.user.id = :userId and rt.revokedAt is null and rt.expiresAt > :now
           """)
    List<RefreshToken> findActiveByUserId(Long userId, Instant now);

    @Query("""
           select rt from RefreshToken rt
           where rt.familyId = :family and rt.revokedAt is null and rt.expiresAt > :now
           """)
    List<RefreshToken> findActiveByFamily(UUID family, Instant now);
}
