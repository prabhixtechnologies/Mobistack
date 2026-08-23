package com.fixflow.pricing.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "price_list_items")
public class PriceListItem extends BaseEntity {

    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Column(name = "price", nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    /** Supports quantity breaks: the highest matching tier applies. */
    @Column(name = "min_quantity", nullable = false)
    private int minQuantity = 1;
}
