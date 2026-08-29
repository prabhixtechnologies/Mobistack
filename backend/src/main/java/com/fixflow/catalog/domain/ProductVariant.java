package com.fixflow.catalog.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A physically distinct item: "iPhone 11 Display / GX / A+ / Black".
 * Owns SKU, barcode, every price and all stock figures.
 *
 * <p>{@code onHandQty} is a cache of the inventory_transactions ledger,
 * maintained inside the same database transaction as the ledger row.
 */
@Getter
@Setter
@Entity
@Table(name = "product_variants")
public class ProductVariant extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "supplier_id")
    private UUID supplierId;

    /**
     * Which part in the shared catalog this variant actually is.
     *
     * <p>The join that makes both halves of MobiStack worth more together: once set, "I have twelve of
     * these, and they fit these forty models" is one query instead of a shop maintaining its own
     * private compatibility list.
     *
     * <p>Nullable on purpose. A shop that has linked nothing keeps working exactly as before, and
     * linking is a per-variant decision rather than a migration that guesses.
     */
    @Column(name = "catalog_component_id")
    private UUID catalogComponentId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "variant_name", nullable = false, length = 160)
    private String variantName;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    /** Trade grade: A+, A, B, OEM, Copy. */
    @Column(name = "grade", length = 24)
    private String grade;

    /** Vendor quality line: GX, ZY, Incell, Original. */
    @Column(name = "quality", length = 40)
    private String quality;

    @Column(name = "color", length = 40)
    private String color;

    // ---- Pricing -----------------------------------------------------
    @Column(name = "cost_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "retail_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal retailPrice = BigDecimal.ZERO;

    @Column(name = "wholesale_price", precision = 14, scale = 2)
    private BigDecimal wholesalePrice;

    @Column(name = "repair_price", precision = 14, scale = 2)
    private BigDecimal repairPrice;

    /** Hard floor: no rule and no manual discount may price below this. */
    @Column(name = "min_price", precision = 14, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "clearance_price", precision = 14, scale = 2)
    private BigDecimal clearancePrice;

    // ---- Stock -------------------------------------------------------
    @Column(name = "on_hand_qty", nullable = false)
    private int onHandQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "available_qty")
    private Integer availableQty;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "max_stock_level")
    private Integer maxStockLevel;

    // ---- Logistics ---------------------------------------------------
    @Column(name = "warranty_days", nullable = false)
    private int warrantyDays;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_tracked", nullable = false)
    private boolean serialTracked;

    @Column(name = "location", length = 64)
    private String location;

    @Column(name = "first_stocked_at")
    private Instant firstStockedAt;

    @Column(name = "last_purchased_at")
    private Instant lastPurchasedAt;

    @Column(name = "last_sold_at")
    private Instant lastSoldAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Falls back to the computed value when the entity has not been reloaded. */
    public int available() {
        return availableQty != null ? availableQty : onHandQty - reservedQty;
    }

    public StockStatus stockStatus(double criticalFactor) {
        return StockStatus.evaluate(available(), reorderLevel, criticalFactor);
    }
}
