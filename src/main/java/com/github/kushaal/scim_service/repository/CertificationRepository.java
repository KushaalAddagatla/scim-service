package com.github.kushaal.scim_service.repository;

import com.github.kushaal.scim_service.model.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    Optional<Certification> findByTokenHash(String tokenHash);

    @Query("SELECT c FROM Certification c WHERE c.status = com.github.kushaal.scim_service.model.entity.Certification.CertStatus.PENDING AND c.expiresAt < :now")
    List<Certification> findExpiredPending(@Param("now") Instant now);
}
