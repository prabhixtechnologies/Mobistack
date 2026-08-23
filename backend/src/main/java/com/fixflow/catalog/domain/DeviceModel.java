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

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "device_models")
public class DeviceModel extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    /** Manufacturer code such as RMX2001, used by suppliers on invoices. */
    @Column(name = "model_code", length = 60)
    private String modelCode;

    @Column(name = "release_year")
    private Integer releaseYear;

    /** Incremented on lookup so frequently serviced phones rank first in search. */
    @Column(name = "popularity", nullable = false)
    private int popularity;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
