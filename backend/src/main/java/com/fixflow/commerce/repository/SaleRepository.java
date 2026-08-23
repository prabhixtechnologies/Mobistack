package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.Sale;
import com.fixflow.commerce.domain.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdAndShopId(UUID id, UUID shopId);

    Optional<Sale> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    Page<Sale> findByShopIdOrderByOccurredAtDesc(UUID shopId, Pageable pageable);

    Page<Sale> findByShopIdAndCustomerIdOrderByOccurredAtDesc(UUID shopId, UUID customerId, Pageable pageable);

    @Query("""
            select coalesce(sum(s.total), 0) from Sale s
            where s.shopId = :shopId and s.status = :status
              and s.occurredAt >= :from and s.occurredAt < :to
            """)
    BigDecimal sumTotal(@Param("shopId") UUID shopId, @Param("status") SaleStatus status,
                        @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(s.profit), 0) from Sale s
            where s.shopId = :shopId and s.status = :status
              and s.occurredAt >= :from and s.occurredAt < :to
            """)
    BigDecimal sumProfit(@Param("shopId") UUID shopId, @Param("status") SaleStatus status,
                         @Param("from") Instant from, @Param("to") Instant to);

    long countByShopIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            UUID shopId, SaleStatus status, Instant from, Instant to);

    @Query("""
            select s from Sale s
            where s.shopId = :shopId and s.status = :status
              and s.occurredAt >= :from and s.occurredAt < :to
            order by s.occurredAt desc
            """)
    Page<Sale> findInRange(@Param("shopId") UUID shopId, @Param("status") SaleStatus status,
                           @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
