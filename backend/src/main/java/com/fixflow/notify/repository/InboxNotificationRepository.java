package com.fixflow.notify.repository;

import com.fixflow.notify.domain.InboxNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InboxNotificationRepository extends JpaRepository<InboxNotification, UUID> {

    Page<InboxNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("update InboxNotification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
