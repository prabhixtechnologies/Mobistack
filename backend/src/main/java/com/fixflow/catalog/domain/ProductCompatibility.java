package com.fixflow.catalog.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Links a product to what it fits. Normally that is a whole compatibility
 * group; {@code deviceModelId} covers the exception where one specific handset
 * takes a part the rest of its group does not.
 *
 * <p>Exactly one of the two targets is set (enforced by a check constraint).
 */
@Getter
@Setter
@Entity
@Table(name = "product_compatibilities")
public class ProductCompatibility extends BaseEntity {

    public enum FitQuality {
        EXACT, COMPATIBLE, REQUIRES_MODIFICATION
    }

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "compatibility_group_id")
    private UUID compatibilityGroupId;

    @Column(name = "device_model_id")
    private UUID deviceModelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_quality", nullable = false, length = 16)
    private FitQuality fitQuality = FitQuality.EXACT;

    @Column(name = "note", length = 255)
    private String note;
}
