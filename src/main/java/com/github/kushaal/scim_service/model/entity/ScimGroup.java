package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scim_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "meta_version", nullable = false)
    @Builder.Default
    private Integer metaVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // CascadeType.ALL + orphanRemoval: adding/removing ScimGroupMembership
    // objects from this list is the single source of truth for membership.
    // Hibernate syncs the DB rows automatically within the transaction.
    @OneToMany(
            mappedBy = "group",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @Builder.Default
    private List<ScimGroupMembership> memberships = new ArrayList<ScimGroupMembership>();
}
