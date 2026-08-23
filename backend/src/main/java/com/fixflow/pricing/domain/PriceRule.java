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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A declarative pricing rule. Every condition is optional and NULL means
 * "don't care"; the engine keeps the highest-priority rule whose conditions
 * all match. Shops can therefore add pricing policy without a code change.
 */
@Getter
@Setter
@Entity
@Table(name = "price_rules")
public class PriceRule extends AuditableEntity {

    public enum ScopeType {
        ALL, CATEGORY, BRAND, PRODUCT, VARIANT
    }

    public enum Strategy {
        /** Quote a field from the variant as-is, e.g. wholesale_price. */
        USE_FIELD,
        PERCENT_OFF,
        AMOUNT_OFF,
        /** cost_price plus a percentage margin. */
        MARKUP_ON_COST,
        FIXED_PRICE
    }

    public enum BaseField {
        COST_PRICE, RETAIL_PRICE, WHOLESALE_PRICE, REPAIR_PRICE, MIN_PRICE, CLEARANCE_PRICE
    }

    public enum TransactionType {
        SALE, REPAIR, ESTIMATE
    }

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** Lower wins. Ties are broken by the more specific scope. */
    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType = ScopeType.ALL;

    @Column(name = "scope_id")
    private UUID scopeId;

    // ---- Conditions (all optional) -----------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_flag", length = 20)
    private PricingFlag pricingFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 20)
    private TransactionType transactionType;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    /** Matches stock sitting unsold for at least this many days. */
    @Column(name = "min_stock_age_days")
    private Integer minStockAgeDays;

    @Column(name = "supplier_id")
    private UUID supplierId;

    // ---- Effect ------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 24)
    private Strategy strategy = Strategy.USE_FIELD;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_field", nullable = false, length = 20)
    private BaseField baseField = BaseField.RETAIL_PRICE;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "percentage", precision = 6, scale = 3)
    private BigDecimal percentage;

    @Column(name = "respect_min_price", nullable = false)
    private boolean respectMinPrice = true;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Narrower scopes win ties; ALL is the fallback. */
    public int specificity() {
        return switch (scopeType) {
            case VARIANT -> 4;
            case PRODUCT -> 3;
            case CATEGORY, BRAND -> 2;
            case ALL -> 1;
        };
    }
}
