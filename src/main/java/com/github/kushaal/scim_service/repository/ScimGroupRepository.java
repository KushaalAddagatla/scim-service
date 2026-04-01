package com.github.kushaal.scim_service.repository;

import com.github.kushaal.scim_service.model.entity.ScimGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID>, JpaSpecificationExecutor<ScimGroup> {

    boolean existsByDisplayName(String displayName);
}
