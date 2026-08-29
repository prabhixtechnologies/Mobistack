package com.fixflow.audit.service;

import com.fixflow.audit.domain.AuditLog;
import com.fixflow.audit.repository.AuditLogRepository;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the business audit trail, e.g.
 * "Rohan changed iPhone 11 Display price from 4,300 to 4,500".
 *
 * <p>Auditing must never break the operation it is recording, so failures are
 * logged and swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, UUID entityId, String summary) {
        record(action, entityType, entityId, summary, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, UUID entityId, String summary,
                       Map<String, Object> before, Map<String, Object> after) {
        try {
            UserPrincipal principal = CurrentUser.find().orElse(null);
            AuditLog entry = new AuditLog();
            entry.setShopId(principal != null ? principal.getShopId() : null);
            entry.setUserId(principal != null ? principal.getId() : null);
            entry.setActorName(principal != null ? principal.getFullName() : "System");
            entry.setAction(action.name());
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setSummary(summary);
            entry.setBeforeData(before);
            entry.setAfterData(after);

            if (entry.getShopId() == null) {
                log.debug("Skipping audit entry with no shop context: {}", summary);
                return;
            }
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to write audit entry for {} {}", entityType, entityId, ex);
        }
    }

    /** For system work (seeding, migrations) that has no authenticated principal. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordForShop(UUID shopId, String actorName, AuditAction action, String entityType,
                              UUID entityId, String summary) {
        try {
            AuditLog entry = new AuditLog();
            entry.setShopId(shopId);
            entry.setActorName(actorName);
            entry.setAction(action.name());
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setSummary(summary);
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to write system audit entry for {} {}", entityType, entityId, ex);
        }
    }
}
