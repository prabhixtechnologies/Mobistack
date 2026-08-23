package com.fixflow.billing.repository;

import com.fixflow.billing.domain.WorkspaceEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkspaceEntitlementRepository extends JpaRepository<WorkspaceEntitlement, UUID> {

    List<WorkspaceEntitlement> findByWorkspaceIdAndActiveTrue(UUID workspaceId);

    boolean existsByWorkspaceIdAndCodeAndActiveTrue(UUID workspaceId, String code);

    java.util.Optional<WorkspaceEntitlement> findByWorkspaceIdAndCode(UUID workspaceId, String code);

    List<WorkspaceEntitlement> findByActiveTrueAndExpiresAtBefore(Instant instant);

    @Query("select distinct e.workspaceId from WorkspaceEntitlement e where e.active = true "
            + "and e.expiresAt is not null and e.expiresAt > :now and e.expiresAt <= :until")
    List<UUID> findWorkspaceIdsExpiringBetween(@Param("now") Instant now, @Param("until") Instant until);
}
