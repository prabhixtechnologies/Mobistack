package com.fixflow.repair.domain;

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
@Table(name = "repair_parts")
public class RepairPart extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "repair_id", nullable = false)
    private UUID repairId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;
}
