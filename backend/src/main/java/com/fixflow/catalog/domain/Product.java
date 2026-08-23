package com.fixflow.catalog.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The sellable concept, e.g. "iPhone 11 Display". Anything that can differ
 * between two physical items on the shelf — SKU, grade, colour, price, stock —
 * lives on {@link ProductVariant} instead.
 */
@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "description")
    private String description;

    @Column(name = "hsn_code", length = 16)
    private String hsnCode;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit = "PCS";

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
