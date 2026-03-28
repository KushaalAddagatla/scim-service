package com.github.kushaal.scim_service.repository;

import com.github.kushaal.scim_service.model.entity.ScimUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScimUserRepository extends JpaRepository<ScimUser, UUID>, JpaSpecificationExecutor<ScimUser> {

    Optional<ScimUser> findByUserName(String userName);

    boolean existsByUserName(String userName);
}