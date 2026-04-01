package com.github.kushaal.scim_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

// @MapsId links each FK column to the matching UUID field inside the
// embedded composite key, so Hibernate writes group_id/user_id once
// (into both the @EmbeddedId and the @JoinColumn) rather than twice.
@Entity
@Table(name = "scim_group_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimGroupMembership {

    @EmbeddedId
    private ScimGroupMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private ScimGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private ScimUser user;

    private String display;
}
