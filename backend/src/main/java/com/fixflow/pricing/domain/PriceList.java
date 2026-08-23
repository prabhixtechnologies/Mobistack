package com.fixflow.pricing.domain;

import com.fixflow.common.domain.AuditableEntity;
import com.fixflow.party.domain.CustomerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An explicit per-variant price sheet (a supplier deal, a festive offer).
 * Takes precedence over rule-based pricing when one of its items matches.
 */
@Getter
@Setter
@Entity
@Table(name = "price_lists")
public class PriceList extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "code", nullable = false, length = 48)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_flag", length = 20)
    private PricingFlag pricingFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 20)
    private CustomerType customerType;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
