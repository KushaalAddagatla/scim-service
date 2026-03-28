package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime timestamp;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(length = 255)
    private String actor;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    // Stored as PostgreSQL JSONB — raw SCIM request body for auditability
    @Column(name = "scim_operation", columnDefinition = "jsonb")
    private String scimOperation;

    @Column(nullable = false, length = 50)
    private String outcome;

    @Column(name = "source_ip", length = 50)
    private String sourceIp;

    @Column(name = "correlation_id")
    private UUID correlationId;
}
