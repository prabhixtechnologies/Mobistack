package com.fixflow.billing.repository;

import com.fixflow.billing.domain.BillingOrder;
import com.fixflow.commerce.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, UUID> {

    List<BillingOrder> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<BillingOrder> findTop3ByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, PaymentStatus status);

    List<BillingOrder> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    Optional<BillingOrder> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<BillingOrder> findByGatewayOrderIdAndWorkspaceId(String gatewayOrderId, UUID workspaceId);

    /**
     * Used by the gateway webhook, which knows the gateway's order id and
     * nothing about our workspaces.
     */
    Optional<BillingOrder> findByGatewayOrderId(String gatewayOrderId);

    Optional<BillingOrder> findByGatewayOrderIdAndUserId(String gatewayOrderId, UUID userId);

    Optional<BillingOrder> findByIdAndUserId(UUID id, UUID userId);

    Optional<BillingOrder> findFirstByWorkspaceIdAndUserIdAndPriceCodeAndStatusAndPurposeOrderByCreatedAtDesc(
            UUID workspaceId, UUID userId, String priceCode, PaymentStatus status, String purpose);

    @Query("""
            select o.status, coalesce(sum(o.amount), 0), count(o)
            from BillingOrder o
            group by o.status
            """)
    List<Object[]> aggregateByStatus();
}
