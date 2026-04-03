package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessHistory {

    public enum AccessStatus { ACTIVE, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ScimUser user;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(name = "access_granted_at", nullable = false)
    private Instant accessGrantedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false)
    @Builder.Default
    private AccessStatus accessStatus = AccessStatus.ACTIVE;
}
