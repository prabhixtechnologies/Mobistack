package com.fixflow.catalog.domain;

import com.fixflow.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * "These models take the same part" — the concept the shopkeeper already
 * writes as "Realme 6 = Realme 6i = Realme 7".
 *
 * <p>A group is scoped to one part category on purpose: two phones can share a
 * display but need different back covers, so a single device-level equivalence
 * list would be wrong.
 */
@Getter
@Setter
@Entity
@Table(name = "compatibility_groups")
public class CompatibilityGroup extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "category_id")
    private UUID categoryId;

    /** Human-readable handle, e.g. REALME_DISPLAY_GROUP_001. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "notes")
    private String notes;

    /** Set once the shop has physically confirmed the parts interchange. */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
