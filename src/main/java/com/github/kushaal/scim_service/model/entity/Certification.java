package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certification {

    public enum CertStatus { PENDING, APPROVED, REVOKED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // The user whose access is under review
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ScimUser user;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    // The manager who receives the review email — nullable (ON DELETE SET NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private ScimUser reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CertStatus status = CertStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    // SHA-256 hash of the raw JWT sent in the email link — raw token is never persisted
    @Column(name = "token_hash", unique = true)
    private String tokenHash;

    @Column(name = "token_used", nullable = false)
    @Builder.Default
    private Boolean tokenUsed = false;
}
