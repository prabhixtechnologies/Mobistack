package com.fixflow.billing.repository;

import com.fixflow.billing.domain.WorkspaceSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkspaceSubscriptionRepository extends JpaRepository<WorkspaceSubscription, UUID> {

    List<WorkspaceSubscription> findByStatusAndPeriodEndBefore(String status, Instant instant);

    List<WorkspaceSubscription> findByStatusAndPeriodEndBetween(String status, Instant from, Instant to);
}
