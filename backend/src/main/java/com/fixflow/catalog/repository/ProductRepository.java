package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndShopId(UUID id, UUID shopId);

    List<Product> findByShopIdAndIdIn(UUID shopId, Collection<UUID> ids);

    long countByShopId(UUID shopId);

    long countByShopIdAndActiveTrue(UUID shopId);

    @Query("""
            select p from Product p
            where p.shopId = :shopId
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:brandId is null or p.brandId = :brandId)
              and (:activeOnly = false or p.active = true)
              and (:query = '' or p.normalizedName like concat('%', :query, '%'))
            """)
    Page<Product> search(@Param("shopId") UUID shopId,
                         @Param("query") String normalizedQuery,
                         @Param("categoryId") UUID categoryId,
                         @Param("brandId") UUID brandId,
                         @Param("activeOnly") boolean activeOnly,
                         Pageable pageable);

    /**
     * Products that fit a device, either through one of its compatibility
     * groups or by a direct model-specific link.
     */
    @Query("""
            select p from Product p
            where p.shopId = :shopId and p.active
              and p.id in (
                  select pc.productId from ProductCompatibility pc
                  where pc.shopId = :shopId
                    and (pc.deviceModelId = :deviceModelId
                         or pc.compatibilityGroupId in (
                             select cgd.compatibilityGroupId from CompatibilityGroupDevice cgd
                             where cgd.deviceModelId = :deviceModelId))
              )
            """)
    List<Product> findCompatibleWithDevice(@Param("shopId") UUID shopId,
                                           @Param("deviceModelId") UUID deviceModelId);
}
