package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CatalogDeviceRepository extends JpaRepository<CatalogDevice, UUID> {

    /**
     * The identity of a device: brand, name and variant together.
     *
     * <p>{@code coalesce} rather than an {@code is null} branch, so a null variant and an empty one
     * collide exactly as the unique index makes them. Two rows differing only in that would be the
     * same phone stored twice, and the second one would quietly collect its own fitments.
     */
    @Query("""
            select d from CatalogDevice d
            where d.brandId = :brandId
              and lower(d.name) = lower(:name)
              and lower(coalesce(d.variant, '')) = lower(coalesce(:variant, ''))
            """)
    Optional<CatalogDevice> findByIdentity(@Param("brandId") UUID brandId,
                                           @Param("name") String name,
                                           @Param("variant") String variant);

    /**
     * Free-text search over names and aliases.
     *
     * <p>Aliases are searched too because customers do not use model names — somebody asks for a
     * "Redmi Note 10" and the row is called something else. Ordered by lookup count, so the common
     * repair is the first result rather than the alphabetically luckiest one.
     */
    @Query("""
            select distinct d from CatalogDevice d
            where lower(d.name) like lower(concat('%', :term, '%'))
               or exists (select 1 from CatalogDeviceAlias a
                          where a.deviceId = d.id
                            and lower(a.alias) like lower(concat('%', :term, '%')))
            order by d.lookupCount desc, d.name asc
            """)
    Page<CatalogDevice> search(@Param("term") String term, Pageable pageable);
}
