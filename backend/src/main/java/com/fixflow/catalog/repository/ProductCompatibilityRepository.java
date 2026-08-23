package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.ProductCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCompatibilityRepository extends JpaRepository<ProductCompatibility, UUID> {

    List<ProductCompatibility> findByProductId(UUID productId);

    List<ProductCompatibility> findByProductIdIn(Collection<UUID> productIds);

    Optional<ProductCompatibility> findByIdAndShopId(UUID id, UUID shopId);

    boolean existsByProductIdAndCompatibilityGroupId(UUID productId, UUID compatibilityGroupId);

    boolean existsByProductIdAndDeviceModelId(UUID productId, UUID deviceModelId);

    long countByCompatibilityGroupId(UUID compatibilityGroupId);

    @Query("select count(pc) from ProductCompatibility pc where pc.shopId = :shopId")
    long countForShop(@Param("shopId") UUID shopId);
}
