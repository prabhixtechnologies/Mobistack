package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.DeviceModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceModelRepository extends JpaRepository<DeviceModel, UUID> {

    @EntityGraph(attributePaths = {"brand"})
    Optional<DeviceModel> findByIdAndShopId(UUID id, UUID shopId);

    @EntityGraph(attributePaths = {"brand"})
    Page<DeviceModel> findByShopId(UUID shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    Page<DeviceModel> findByShopIdAndBrandId(UUID shopId, UUID brandId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    List<DeviceModel> findByShopIdAndIdIn(UUID shopId, java.util.Collection<UUID> ids);

    @EntityGraph(attributePaths = {"brand"})
    @Query("""
            select d from DeviceModel d
            where d.shopId = :shopId and lower(d.name) = lower(:name) and d.brand.id = :brandId
              and (
                    (:hasVariant = false and (d.variant is null or d.variant = ''))
                    or (:hasVariant = true and lower(d.variant) = lower(:variant))
                  )
            """)
    Optional<DeviceModel> findByShopIdBrandNameAndVariant(@Param("shopId") UUID shopId,
                                                          @Param("brandId") UUID brandId,
                                                          @Param("name") String name,
                                                          @Param("hasVariant") boolean hasVariant,
                                                          @Param("variant") String variant);

    @Query("""
            select d from DeviceModel d
            where d.shopId = :shopId and lower(d.name) = lower(:name) and d.brand.id = :brandId
            """)
    Optional<DeviceModel> findByShopIdBrandAndName(@Param("shopId") UUID shopId,
                                                   @Param("brandId") UUID brandId,
                                                   @Param("name") String name);

    long countByShopId(UUID shopId);

    /**
     * Ranked fuzzy lookup across model names and their aliases.
     *
     * <p>Prefix hits rank above trigram similarity, so typing "realme 6" puts
     * Realme 6 first rather than Realme 6 Pro; ties break on how often the shop
     * actually services the model. Both {@code LIKE '%x%'} and the {@code %}
     * similarity operator are served by the GIN trigram indexes from V2.
     *
     * <p>Aliases are quoted to survive PostgreSQL's lower-casing of bare
     * identifiers, which the projection binds by exact column label.
     */
    @Query(value = """
            select dm.id            as "id",
                   dm.name          as "name",
                   b.id             as "brandId",
                   b.name           as "brandName",
                   dm.model_code    as "modelCode",
                   dm.variant       as "variant",
                   dm.popularity    as "popularity",
                   greatest(
                       similarity(dm.normalized_name, :query),
                       coalesce((select max(similarity(da.normalized_alias, :query))
                                 from device_aliases da
                                 where da.device_model_id = dm.id), 0)
                   )                as "score"
            from device_models dm
                     join brands b on b.id = dm.brand_id
            where dm.shop_id = :shopId
              and dm.active
              and (
                  dm.normalized_name like '%' || :query || '%'
                      or dm.normalized_name % :query
                      or fixflow_normalize(coalesce(dm.variant, '')) like '%' || :query || '%'
                      or exists (select 1 from device_aliases da3
                                 where da3.device_model_id = dm.id
                                   and (da3.normalized_alias like '%' || :query || '%'
                                        or da3.normalized_alias % :query))
                  )
            order by (dm.normalized_name like :query || '%'
                          or exists (select 1 from device_aliases da2
                                     where da2.device_model_id = dm.id
                                       and da2.normalized_alias like :query || '%')) desc,
                     "score" desc,
                     dm.popularity desc,
                     dm.name
            limit :maxResults
            """, nativeQuery = true)
    List<DeviceSearchRow> searchDevices(@Param("shopId") UUID shopId,
                                        @Param("query") String normalizedQuery,
                                        @Param("maxResults") int maxResults);

    /**
     * Every model sharing at least one compatibility group with the given
     * device: the "Realme 6 = Realme 6i = Realme 7" list the shopkeeper expects.
     *
     * <p>Both the peer model and the group are held to the shop. Group membership
     * is keyed on device alone, so without those filters a group that ever
     * straddled two shops would surface the other shop's model names here.
     */
    @Query(value = """
            select distinct dm.id         as "id",
                            dm.name       as "name",
                            b.id          as "brandId",
                            b.name        as "brandName",
                            dm.model_code as "modelCode",
                            dm.variant    as "variant",
                            dm.popularity as "popularity",
                            cast(1 as real) as "score"
            from compatibility_group_devices source
                     join compatibility_group_devices peer
                          on peer.compatibility_group_id = source.compatibility_group_id
                     join compatibility_groups cg on cg.id = source.compatibility_group_id
                     join device_models dm on dm.id = peer.device_model_id
                     join brands b on b.id = dm.brand_id
            where source.device_model_id = :deviceModelId
              and cg.active
              and cg.shop_id = :shopId
              and dm.active
              and dm.shop_id = :shopId
              and dm.id <> :deviceModelId
            order by dm.name
            """, nativeQuery = true)
    List<DeviceSearchRow> findCompatibleModels(@Param("shopId") UUID shopId,
                                               @Param("deviceModelId") UUID deviceModelId);

    /** Cheap popularity signal: bump the model each time it is looked up. */
    @Modifying
    @Query("update DeviceModel d set d.popularity = d.popularity + 1 where d.id = :id")
    void incrementPopularity(@Param("id") UUID id);

    interface DeviceSearchRow {
        UUID getId();

        String getName();

        UUID getBrandId();

        String getBrandName();

        String getModelCode();

        String getVariant();

        int getPopularity();

        float getScore();
    }
}
