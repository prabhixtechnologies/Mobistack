package com.fixflow.catalog.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "brands")
public class Brand extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Computed by Postgres via fixflow_normalize(); read-only from Java. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "color", length = 9)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
