package com.fixflow.catalog.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.UUID;

/**
 * An alternate name a customer or supplier might use for the same handset:
 * "Realme 6i", "RMX2002", "Narzo 20". Aliases are searchable, so typing any of
 * them lands on the canonical model.
 */
@Getter
@Setter
@Entity
@Table(name = "device_aliases")
public class DeviceAlias extends BaseEntity {

    public enum Source {
        MANUAL, IMPORT, SYSTEM
    }

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "device_model_id", nullable = false)
    private UUID deviceModelId;

    @Column(name = "alias", nullable = false, length = 120)
    private String alias;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_alias")
    private String normalizedAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 24)
    private Source source = Source.MANUAL;
}
