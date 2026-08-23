package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.CompatibilityGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompatibilityGroupRepository extends JpaRepository<CompatibilityGroup, UUID> {

    Optional<CompatibilityGroup> findByIdAndShopId(UUID id, UUID shopId);

    Optional<CompatibilityGroup> findByShopIdAndCode(UUID shopId, String code);

    boolean existsByShopIdAndCode(UUID shopId, String code);

    Page<CompatibilityGroup> findByShopId(UUID shopId, Pageable pageable);

    Page<CompatibilityGroup> findByShopIdAndCategoryId(UUID shopId, UUID categoryId, Pageable pageable);

    long countByShopId(UUID shopId);

    /** Groups the given device belongs to, used to render its compatibility card. */
    @Query("""
            select g from CompatibilityGroup g
            where g.shopId = :shopId and g.active
              and g.id in (select d.compatibilityGroupId from CompatibilityGroupDevice d
                           where d.deviceModelId = :deviceModelId)
            order by g.name
            """)
    List<CompatibilityGroup> findForDevice(@Param("shopId") UUID shopId,
                                           @Param("deviceModelId") UUID deviceModelId);
}
