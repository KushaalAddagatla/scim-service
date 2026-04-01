package com.github.kushaal.scim_service.repository;

import com.github.kushaal.scim_service.model.entity.ScimGroupMembership;
import com.github.kushaal.scim_service.model.entity.ScimGroupMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScimGroupMembershipRepository extends JpaRepository<ScimGroupMembership, ScimGroupMembershipId> {

    void deleteByIdGroupIdAndIdUserId(UUID groupId, UUID userId);
}
