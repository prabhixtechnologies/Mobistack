package com.fixflow.audit.repository;

import com.fixflow.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Page<AuditLog> findByShopIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID shopId, String entityType, UUID entityId, Pageable pageable);

    @Query("""
            select a from AuditLog a
            where a.shopId = :shopId
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
              and a.createdAt between :from and :to
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("shopId") UUID shopId,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
