package com.fixflow.notify.repository;

import com.fixflow.notify.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserIdOrderByEventTypeAsc(UUID userId);

    Optional<NotificationPreference> findByUserIdAndEventType(UUID userId, String eventType);
}
