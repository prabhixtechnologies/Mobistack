package com.fixflow.catalog.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A part category such as "Folder / Display / Combo". Seeded per shop from
 * {@link DefaultCategories} so every shop can rename, recolour, reorder or
 * disable them and add its own.
 */
@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, length = 48)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "icon", length = 48)
    private String icon;

    @Column(name = "color", length = 9)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /**
     * True for parts looked up through compatibility groups (displays,
     * batteries). False for generic goods such as cables or cleaning kits.
     */
    @Column(name = "compatibility_relevant", nullable = false)
    private boolean compatibilityRelevant = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
