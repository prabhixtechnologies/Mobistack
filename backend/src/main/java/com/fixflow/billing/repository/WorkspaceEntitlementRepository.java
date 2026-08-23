package com.fixflow.billing.repository;

import com.fixflow.billing.domain.WorkspaceEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkspaceEntitlementRepository extends JpaRepository<WorkspaceEntitlement, UUID> {

    List<WorkspaceEntitlement> findByWorkspaceIdAndActiveTrue(UUID workspaceId);

    boolean existsByWorkspaceIdAndCodeAndActiveTrue(UUID workspaceId, String code);

    java.util.Optional<WorkspaceEntitlement> findByWorkspaceIdAndCode(UUID workspaceId, String code);
}
