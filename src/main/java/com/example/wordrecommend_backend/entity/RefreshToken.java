package com.example.wordrecommend_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "ux_rt_jti_hash", columnNames = "jti_hash"))
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true) // 只輸出我們標記的欄位，避免印出 LAZY 關聯
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // 只用 id 作為等值判斷
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // 關聯使用者（LAZY 很重要，避免不必要查詢）
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 識別與鏈結
    @Column(nullable = false)
    @ToString.Include
    private UUID jti;

    @Column(name = "jti_hash", length = 128, nullable = false)
    private String jtiHash;

    @Column(name = "family_id", nullable = false)
    @ToString.Include
    private UUID familyId;

    @Column(name = "parent_jti")
    private UUID parentJti;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    // 狀態時間
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    // 觀測
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress; // 若要用 PostgreSQL inet，可另外定義自訂型別處理
}
