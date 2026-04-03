package com.github.kushaal.scim_service.repository;

import com.github.kushaal.scim_service.model.entity.AccessHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessHistoryRepository extends JpaRepository<AccessHistory, UUID> {

    @Query("SELECT a FROM AccessHistory a WHERE a.lastAccessedAt < :cutoff AND a.accessStatus = com.github.kushaal.scim_service.model.entity.AccessHistory.AccessStatus.ACTIVE")
    List<AccessHistory> findStaleActive(@Param("cutoff") Instant cutoff);
}
