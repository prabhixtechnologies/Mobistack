package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findByIdAndShopId(UUID id, UUID shopId);

    @EntityGraph(attributePaths = {"product"})
    List<ProductVariant> findByProductIdOrderByVariantNameAsc(UUID productId);

    /**
     * Batch form of {@link #findByIdAndShopId}, so a page of documents resolves its
     * item names in one query instead of one per line.
     */
    @EntityGraph(attributePaths = {"product"})
    List<ProductVariant> findByShopIdAndIdIn(UUID shopId, Collection<UUID> ids);

    @EntityGraph(attributePaths = {"product"})
    List<ProductVariant> findByShopIdAndProductIdOrderByVariantNameAsc(UUID shopId, UUID productId);

    @EntityGraph(attributePaths = {"product"})
    List<ProductVariant> findByProductIdInAndActiveTrue(Collection<UUID> productIds);

    @EntityGraph(attributePaths = {"product"})
    @Query("select v from ProductVariant v where v.shopId = :shopId and upper(v.sku) = upper(:sku)")
    Optional<ProductVariant> findBySku(@Param("shopId") UUID shopId, @Param("sku") String sku);

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findByShopIdAndBarcode(UUID shopId, String barcode);

    boolean existsByShopIdAndSkuIgnoreCase(UUID shopId, String sku);

    boolean existsByShopIdAndBarcode(UUID shopId, String barcode);

    /**
     * Locked read used by every stock mutation, so two concurrent sales of the
     * last unit cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id and v.shopId = :shopId")
    Optional<ProductVariant> findForStockUpdate(@Param("id") UUID id, @Param("shopId") UUID shopId);

    /** Every variant fitting a device, via compatibility group or direct link. */
    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.shopId = :shopId and v.active
              and v.product.active
              and v.product.id in (
                  select pc.productId from ProductCompatibility pc
                  where pc.shopId = :shopId
                    and (pc.deviceModelId = :deviceModelId
                         or pc.compatibilityGroupId in (
                             select cgd.compatibilityGroupId from CompatibilityGroupDevice cgd
                             where cgd.deviceModelId = :deviceModelId))
              )
            order by v.product.name, v.variantName
            """)
    List<ProductVariant> findCompatibleWithDevice(@Param("shopId") UUID shopId,
                                                  @Param("deviceModelId") UUID deviceModelId);

    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.shopId = :shopId
              and (:activeOnly = false or (v.active = true and v.product.active = true))
              and (:categoryId is null or v.product.categoryId = :categoryId)
              and (:brandId is null or v.product.brandId = :brandId)
              and (:supplierId is null or v.supplierId = :supplierId)
              and (:query = ''
                   or v.normalizedName like concat('%', :query, '%')
                   or v.product.normalizedName like concat('%', :query, '%')
                   or upper(v.sku) like concat('%', upper(:rawQuery), '%')
                   or v.barcode = :rawQuery)
              and (:lowStockOnly = false or v.onHandQty <= v.reorderLevel)
              and (:inStockOnly = false or v.onHandQty > 0)
            """)
    Page<ProductVariant> search(@Param("shopId") UUID shopId,
                                @Param("query") String normalizedQuery,
                                @Param("rawQuery") String rawQuery,
                                @Param("categoryId") UUID categoryId,
                                @Param("brandId") UUID brandId,
                                @Param("supplierId") UUID supplierId,
                                @Param("activeOnly") boolean activeOnly,
                                @Param("lowStockOnly") boolean lowStockOnly,
                                @Param("inStockOnly") boolean inStockOnly,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.shopId = :shopId and v.active and v.onHandQty <= v.reorderLevel
            order by (v.onHandQty - v.reservedQty) asc, v.product.name asc
            """)
    Page<ProductVariant> findLowStock(@Param("shopId") UUID shopId, Pageable pageable);

    /**
     * Dead stock: still on the shelf, but nothing sold since the cutoff.
     * A variant that has never sold counts from when it was first stocked.
     */
    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.shopId = :shopId and v.active and v.onHandQty > 0
              and ((v.lastSoldAt is null and (v.firstStockedAt is null or v.firstStockedAt < :cutoff))
                   or v.lastSoldAt < :cutoff)
            order by v.onHandQty * v.costPrice desc
            """)
    Page<ProductVariant> findDeadStock(@Param("shopId") UUID shopId,
                                       @Param("cutoff") Instant cutoff,
                                       Pageable pageable);

    long countByShopIdAndActiveTrue(UUID shopId);

    @Query("select coalesce(sum(v.onHandQty), 0) from ProductVariant v where v.shopId = :shopId and v.active")
    long sumStockUnits(@Param("shopId") UUID shopId);

    @Query("""
            select coalesce(sum(v.onHandQty * v.costPrice), 0)
            from ProductVariant v where v.shopId = :shopId and v.active
            """)
    BigDecimal sumStockValueAtCost(@Param("shopId") UUID shopId);

    @Query("""
            select coalesce(sum(v.onHandQty * v.retailPrice), 0)
            from ProductVariant v where v.shopId = :shopId and v.active
            """)
    BigDecimal sumStockValueAtRetail(@Param("shopId") UUID shopId);

    @Query("""
            select count(v) from ProductVariant v
            where v.shopId = :shopId and v.active and v.onHandQty <= 0
            """)
    long countOutOfStock(@Param("shopId") UUID shopId);

    @Query("""
            select count(v) from ProductVariant v
            where v.shopId = :shopId and v.active and v.onHandQty > 0 and v.onHandQty <= v.reorderLevel
            """)
    long countLowStock(@Param("shopId") UUID shopId);
}
