package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.Purchase;
import com.fixflow.commerce.domain.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Optional<Purchase> findByIdAndShopId(UUID id, UUID shopId);

    Optional<Purchase> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    Page<Purchase> findByShopIdOrderByReceivedAtDesc(UUID shopId, Pageable pageable);

    @Query("""
            select coalesce(sum(p.total), 0) from Purchase p
            where p.shopId = :shopId and p.status = :status
              and p.receivedAt >= :from and p.receivedAt < :to
            """)
    BigDecimal sumTotal(@Param("shopId") UUID shopId, @Param("status") PurchaseStatus status,
                        @Param("from") Instant from, @Param("to") Instant to);
}
