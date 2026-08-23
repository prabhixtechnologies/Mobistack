package com.fixflow.notify.repository;

import com.fixflow.notify.domain.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);

    List<PushDevice> findByUserIdAndExpoPushTokenIsNotNull(UUID userId);
}
