package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogDeviceAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogDeviceAliasRepository extends JpaRepository<CatalogDeviceAlias, UUID> {

    List<CatalogDeviceAlias> findByDeviceId(UUID deviceId);

    @Query("select a from CatalogDeviceAlias a where lower(a.alias) = lower(:alias)")
    Optional<CatalogDeviceAlias> findByAlias(@Param("alias") String alias);
}
