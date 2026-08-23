package com.fixflow.billing.repository;

import com.fixflow.billing.domain.BillingPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPriceRepository extends JpaRepository<BillingPrice, UUID> {

    List<BillingPrice> findByActiveTrue();

    Optional<BillingPrice> findByCodeAndActiveTrue(String code);
}
