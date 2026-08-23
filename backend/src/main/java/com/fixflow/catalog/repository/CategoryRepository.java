package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByShopIdOrderBySortOrderAscNameAsc(UUID shopId);

    List<Category> findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID shopId);

    Optional<Category> findByIdAndShopId(UUID id, UUID shopId);

    Optional<Category> findByShopIdAndCode(UUID shopId, String code);

    boolean existsByShopIdAndCode(UUID shopId, String code);

    long countByShopId(UUID shopId);
}
