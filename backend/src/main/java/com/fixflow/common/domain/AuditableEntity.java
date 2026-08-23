package com.fixflow.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable business entities. Optimistic locking matters here because the
 * Android client syncs edits that were made while offline.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
