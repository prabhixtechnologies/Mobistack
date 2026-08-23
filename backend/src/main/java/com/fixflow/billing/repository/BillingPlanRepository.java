package com.fixflow.billing.repository;

import com.fixflow.billing.domain.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    Optional<BillingPlan> findByCode(String code);

    List<BillingPlan> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<BillingPlan> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);
}
