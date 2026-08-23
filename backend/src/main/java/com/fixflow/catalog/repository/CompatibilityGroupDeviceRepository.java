package com.fixflow.catalog.repository;

import com.fixflow.catalog.domain.CompatibilityGroupDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompatibilityGroupDeviceRepository extends JpaRepository<CompatibilityGroupDevice, UUID> {

    List<CompatibilityGroupDevice> findByCompatibilityGroupId(UUID compatibilityGroupId);

    List<CompatibilityGroupDevice> findByCompatibilityGroupIdIn(Collection<UUID> compatibilityGroupIds);

    List<CompatibilityGroupDevice> findByDeviceModelId(UUID deviceModelId);

    Optional<CompatibilityGroupDevice> findByCompatibilityGroupIdAndDeviceModelId(UUID groupId, UUID deviceModelId);

    boolean existsByCompatibilityGroupIdAndDeviceModelId(UUID groupId, UUID deviceModelId);

    void deleteByCompatibilityGroupIdAndDeviceModelId(UUID groupId, UUID deviceModelId);

    @Query("select count(d) from CompatibilityGroupDevice d where d.compatibilityGroupId = :groupId")
    long countDevices(@Param("groupId") UUID groupId);
}
