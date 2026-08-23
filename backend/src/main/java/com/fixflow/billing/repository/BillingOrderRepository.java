package com.fixflow.billing.repository;

import com.fixflow.billing.domain.BillingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, UUID> {

    List<BillingOrder> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<BillingOrder> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<BillingOrder> findByGatewayOrderIdAndWorkspaceId(String gatewayOrderId, UUID workspaceId);
}
