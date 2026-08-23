package com.fixflow.notify.repository;

import com.fixflow.notify.domain.NotificationOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    Page<NotificationOutbox> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);
}
