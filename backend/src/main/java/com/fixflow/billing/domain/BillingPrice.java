package com.fixflow.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_prices")
public class BillingPrice {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "interval", nullable = false, length = 20)
    private String interval;

    @Column(name = "entitlement", nullable = false, length = 40)
    private String entitlement;

    @Column(nullable = false)
    private boolean active = true;
}
