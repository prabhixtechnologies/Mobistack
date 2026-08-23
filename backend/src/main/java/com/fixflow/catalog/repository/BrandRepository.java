package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    List<Brand> findByShopIdOrderBySortOrderAscNameAsc(UUID shopId);

    List<Brand> findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID shopId);

    Optional<Brand> findByIdAndShopId(UUID id, UUID shopId);

    @Query("select b from Brand b where b.shopId = :shopId and lower(b.name) = lower(:name)")
    Optional<Brand> findByShopIdAndName(@Param("shopId") UUID shopId, @Param("name") String name);

    long countByShopId(UUID shopId);
}
