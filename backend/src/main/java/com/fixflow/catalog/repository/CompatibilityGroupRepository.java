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

    /** Batch form, so resolving a product's group names takes one query. */
    List<CompatibilityGroup> findByShopIdAndIdIn(UUID shopId, java.util.Collection<UUID> ids);

    Optional<CompatibilityGroup> findByShopIdAndCode(UUID shopId, String code);

    boolean existsByShopIdAndCode(UUID shopId, String code);

    Page<CompatibilityGroup> findByShopId(UUID shopId, Pageable pageable);

    Page<CompatibilityGroup> findByShopIdAndCategoryId(UUID shopId, UUID categoryId, Pageable pageable);

    Page<CompatibilityGroup> findByShopIdAndActiveTrueOrderByCreatedAtAsc(UUID shopId, Pageable pageable);

    Page<CompatibilityGroup> findByShopIdAndCategoryIdAndActiveTrueOrderByCreatedAtAsc(
            UUID shopId, UUID categoryId, Pageable pageable);

    long countByShopId(UUID shopId);

    long countByShopIdAndActiveTrue(UUID shopId);

    @Query("""
            select g.categoryId as categoryId, count(g) as groupCount
            from CompatibilityGroup g
            where g.shopId = :shopId and g.active = true
            group by g.categoryId
            """)
    List<CategoryGroupCount> countActiveByCategory(@Param("shopId") UUID shopId);

    @Query(
            value = """
                    select distinct g from CompatibilityGroup g
                    where g.shopId = :shopId
                      and g.active = true
                      and (
                            lower(g.name) like concat('%', :q, '%')
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceModel m
                                where d.compatibilityGroupId = g.id
                                  and m.id = d.deviceModelId
                                  and m.shopId = :shopId
                                  and (
                                        lower(m.name) like concat('%', :q, '%')
                                        or lower(coalesce(m.variant, '')) like concat('%', :q, '%')
                                        or lower(m.brand.name) like concat('%', :q, '%')
                                        or lower(concat(m.brand.name, ' ', m.name, ' ', coalesce(m.variant, ''))) like concat('%', :q, '%')
                                      )
                            )
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceAlias a
                                where d.compatibilityGroupId = g.id
                                  and a.deviceModelId = d.deviceModelId
                                  and lower(a.alias) like concat('%', :q, '%')
                            )
                          )
                    order by g.createdAt asc, g.name asc
                    """,
            countQuery = """
                    select count(distinct g) from CompatibilityGroup g
                    where g.shopId = :shopId
                      and g.active = true
                      and (
                            lower(g.name) like concat('%', :q, '%')
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceModel m
                                where d.compatibilityGroupId = g.id
                                  and m.id = d.deviceModelId
                                  and m.shopId = :shopId
                                  and (
                                        lower(m.name) like concat('%', :q, '%')
                                        or lower(coalesce(m.variant, '')) like concat('%', :q, '%')
                                        or lower(m.brand.name) like concat('%', :q, '%')
                                        or lower(concat(m.brand.name, ' ', m.name, ' ', coalesce(m.variant, ''))) like concat('%', :q, '%')
                                      )
                            )
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceAlias a
                                where d.compatibilityGroupId = g.id
                                  and a.deviceModelId = d.deviceModelId
                                  and lower(a.alias) like concat('%', :q, '%')
                            )
                          )
                    """
    )
    Page<CompatibilityGroup> search(@Param("shopId") UUID shopId,
                                    @Param("q") String q,
                                    Pageable pageable);

    @Query(
            value = """
                    select distinct g from CompatibilityGroup g
                    where g.shopId = :shopId
                      and g.active = true
                      and g.categoryId = :categoryId
                      and (
                            lower(g.name) like concat('%', :q, '%')
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceModel m
                                where d.compatibilityGroupId = g.id
                                  and m.id = d.deviceModelId
                                  and m.shopId = :shopId
                                  and (
                                        lower(m.name) like concat('%', :q, '%')
                                        or lower(coalesce(m.variant, '')) like concat('%', :q, '%')
                                        or lower(m.brand.name) like concat('%', :q, '%')
                                        or lower(concat(m.brand.name, ' ', m.name, ' ', coalesce(m.variant, ''))) like concat('%', :q, '%')
                                      )
                            )
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceAlias a
                                where d.compatibilityGroupId = g.id
                                  and a.deviceModelId = d.deviceModelId
                                  and lower(a.alias) like concat('%', :q, '%')
                            )
                          )
                    order by g.createdAt asc, g.name asc
                    """,
            countQuery = """
                    select count(distinct g) from CompatibilityGroup g
                    where g.shopId = :shopId
                      and g.active = true
                      and g.categoryId = :categoryId
                      and (
                            lower(g.name) like concat('%', :q, '%')
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceModel m
                                where d.compatibilityGroupId = g.id
                                  and m.id = d.deviceModelId
                                  and m.shopId = :shopId
                                  and (
                                        lower(m.name) like concat('%', :q, '%')
                                        or lower(coalesce(m.variant, '')) like concat('%', :q, '%')
                                        or lower(m.brand.name) like concat('%', :q, '%')
                                        or lower(concat(m.brand.name, ' ', m.name, ' ', coalesce(m.variant, ''))) like concat('%', :q, '%')
                                      )
                            )
                            or exists (
                                select 1 from CompatibilityGroupDevice d, DeviceAlias a
                                where d.compatibilityGroupId = g.id
                                  and a.deviceModelId = d.deviceModelId
                                  and lower(a.alias) like concat('%', :q, '%')
                            )
                          )
                    """
    )
    Page<CompatibilityGroup> searchInCategory(@Param("shopId") UUID shopId,
                                              @Param("categoryId") UUID categoryId,
                                              @Param("q") String q,
                                              Pageable pageable);

    interface CategoryGroupCount {
        UUID getCategoryId();

        long getGroupCount();
    }

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
