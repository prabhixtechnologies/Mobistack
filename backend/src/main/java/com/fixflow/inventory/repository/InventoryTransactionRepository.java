package com.fixflow.inventory.repository;

import com.fixflow.inventory.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Page<InventoryTransaction> findByShopIdOrderByOccurredAtDesc(UUID shopId, Pageable pageable);

    /**
     * Scoped by shop as well as variant. A variant id arriving from a query string
     * is not proof that the caller owns the variant.
     */
    Page<InventoryTransaction> findByShopIdAndProductVariantIdOrderByOccurredAtDesc(
            UUID shopId, UUID variantId, Pageable pageable);

    List<InventoryTransaction> findByReferenceTypeAndReferenceId(
            com.fixflow.inventory.domain.InventoryReferenceType referenceType, UUID referenceId);

    /** Offline replay guard: the same key must never post stock twice. */
    Optional<InventoryTransaction> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    /**
     * Recomputes on-hand stock straight from the ledger. Used by the
     * reconciliation endpoint to prove the cached figure is still correct.
     */
    @Query("""
            select coalesce(sum(t.onHandDelta), 0) from InventoryTransaction t
            where t.productVariantId = :variantId
            """)
    int sumOnHandDelta(@Param("variantId") UUID variantId);

    @Query("""
            select coalesce(sum(t.reservedDelta), 0) from InventoryTransaction t
            where t.productVariantId = :variantId
            """)
    int sumReservedDelta(@Param("variantId") UUID variantId);

    @Query("""
            select count(t) from InventoryTransaction t
            where t.shopId = :shopId and t.occurredAt >= :from and t.occurredAt < :to
            """)
    long countInPeriod(@Param("shopId") UUID shopId,
                       @Param("from") Instant from,
                       @Param("to") Instant to);
}
