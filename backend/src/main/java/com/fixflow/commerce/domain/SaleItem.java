package com.fixflow.commerce.domain;

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
@Table(name = "sale_items")
public class SaleItem extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "profit", nullable = false, precision = 14, scale = 2)
    private BigDecimal profit = BigDecimal.ZERO;
}
