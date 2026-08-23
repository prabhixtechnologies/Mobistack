package com.fixflow.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "workspace_entitlements")
public class WorkspaceEntitlement {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "source_order_id")
    private UUID sourceOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
