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
@Table(name = "shop_subscriptions")
public class WorkspaceSubscription {

    public static final String NONE = "NONE";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAST_DUE = "PAST_DUE";

    @Id
    @Column(name = "shop_id")
    private UUID workspaceId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(nullable = false, length = 20)
    private String status = NONE;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "source_order_id")
    private UUID sourceOrderId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
