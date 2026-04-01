package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

// JPA requires @Embeddable composite keys to implement Serializable,
// and to have correct equals/hashCode so the persistence context can
// detect identity. Lombok's @EqualsAndHashCode covers both UUID fields.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ScimGroupMembershipId implements Serializable {

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "user_id")
    private UUID userId;
}
