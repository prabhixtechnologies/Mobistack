package com.fixflow.repair.repository;

import com.fixflow.repair.domain.RepairJob;
import com.fixflow.repair.domain.RepairStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RepairJobRepository extends JpaRepository<RepairJob, UUID> {

    Optional<RepairJob> findByIdAndShopId(UUID id, UUID shopId);

    Optional<RepairJob> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    Page<RepairJob> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Page<RepairJob> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, RepairStatus status, Pageable pageable);

    long countByShopIdAndStatus(UUID shopId, RepairStatus status);

    @Query("""
            select coalesce(sum(r.total), 0) from RepairJob r
            where r.shopId = :shopId and r.status = :status
              and r.deliveredAt >= :from and r.deliveredAt < :to
            """)
    BigDecimal sumDeliveredTotal(@Param("shopId") UUID shopId, @Param("status") RepairStatus status,
                                 @Param("from") Instant from, @Param("to") Instant to);
}
