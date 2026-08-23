package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.DeviceAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceAliasRepository extends JpaRepository<DeviceAlias, UUID> {

    List<DeviceAlias> findByDeviceModelIdOrderByAliasAsc(UUID deviceModelId);

    List<DeviceAlias> findByDeviceModelIdInOrderByAliasAsc(Collection<UUID> deviceModelIds);

    Optional<DeviceAlias> findByIdAndShopId(UUID id, UUID shopId);

    @Query("select a from DeviceAlias a where a.shopId = :shopId and lower(a.alias) = lower(:alias)")
    Optional<DeviceAlias> findByShopIdAndAlias(@Param("shopId") UUID shopId, @Param("alias") String alias);

    long countByShopId(UUID shopId);
}
