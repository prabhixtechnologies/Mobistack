package com.fixflow.commons.domain;

import com.fixflow.common.domain.AuditableEntity;
import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The shared compatibility catalog.
 *
 * <p>None of these carry a {@code shopId}, and that absence is the design. Whether a screen fits a
 * phone is true for everyone or true for nobody; scoping it per shop meant a hundred shops maintaining
 * a hundred private copies of the same fact, which is a hundred times the work for a hundredth of the
 * coverage. The per-shop {@code Brand}, {@code DeviceModel} and {@code CompatibilityGroup} entities
 * still exist and still work — a shop opts in by linking its variants to a {@link CatalogComponent}.
 *
 * <p>Grouped in one file because they are one model and are always read together; splitting them into
 * six files with three lines of imports each would make the shape harder to see, not easier.
 */
public final class CatalogEntities {

    private CatalogEntities() {
    }

    /** How well a part fits, when it fits at all. */
    public enum FitQuality {
        /** The part the manufacturer used. */
        EXACT,
        /** A different part that works as-is. */
        COMPATIBLE,
        /** Works after trimming, re-pinning or moving a connector. Worth saying out loud. */
        REQUIRES_MODIFICATION
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "catalog_brands")
    public static class CatalogBrand extends AuditableEntity {

        @Column(name = "name", nullable = false, length = 80)
        private String name;

        @Column(name = "logo_url")
        private String logoUrl;
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "catalog_devices")
    public static class CatalogDevice extends AuditableEntity {

        @Column(name = "brand_id", nullable = false)
        private UUID brandId;

        @Column(name = "name", nullable = false, length = 120)
        private String name;

        /**
         * Part of the device's identity, not a note.
         *
         * <p>Two phones sold under one name with different panels are different devices for this
         * purpose, and treating them as one is how a shop ends up ordering the wrong screen.
         */
        @Column(name = "variant", length = 40)
        private String variant;

        @Column(name = "model_code", length = 60)
        private String modelCode;

        @Column(name = "release_year")
        private Integer releaseYear;

        @Column(name = "lookup_count", nullable = false)
        private long lookupCount;
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "catalog_device_aliases")
    public static class CatalogDeviceAlias extends BaseEntity {

        @Column(name = "device_id", nullable = false)
        private UUID deviceId;

        @Column(name = "alias", nullable = false, length = 120)
        private String alias;
    }

    /**
     * A part, described once for everyone.
     *
     * <p>Not a product. A product is something a shop sells, with a price, a supplier and a margin;
     * twenty shops selling the same screen are selling one component.
     */
    @Getter
    @Setter
    @Entity
    @Table(name = "catalog_components")
    public static class CatalogComponent extends AuditableEntity {

        /**
         * Matches {@code categories.code}, a stable string rather than one shop's category row id.
         * A global component cannot borrow a tenant's primary key.
         */
        @Column(name = "category_code", nullable = false, length = 64)
        private String categoryCode;

        @Column(name = "name", nullable = false, length = 160)
        private String name;

        @Column(name = "description")
        private String description;

        /**
         * Panel type, connector count, colour — whatever distinguishes two parts that fit the same
         * phone. JSONB because the meaningful attributes differ per category, and columns would mean a
         * migration every time a new kind of part appears.
         */
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "attributes", nullable = false)
        private Map<String, Object> attributes = Map.of();
    }

    /** The edge: this component fits that device. */
    @Getter
    @Setter
    @Entity
    @Table(name = "catalog_fitments")
    public static class CatalogFitment extends AuditableEntity {

        @Column(name = "component_id", nullable = false)
        private UUID componentId;

        @Column(name = "device_id", nullable = false)
        private UUID deviceId;

        @Enumerated(EnumType.STRING)
        @Column(name = "fit_quality", nullable = false, length = 24)
        private FitQuality fitQuality = FitQuality.EXACT;

        /**
         * How many people have said this held in practice.
         *
         * <p>Counts rather than a boolean, because "someone said so once" and "forty shops fitted it
         * and two disputed it" are different claims, and whoever is deciding whether to order the part
         * needs to tell them apart.
         */
        @Column(name = "confirmations", nullable = false)
        private int confirmations;

        @Column(name = "disputes", nullable = false)
        private int disputes;

        @Column(name = "verified_by")
        private UUID verifiedBy;

        @Column(name = "verified_at")
        private Instant verifiedAt;

        /**
         * Kept visible rather than deleted. An edge under dispute is information; a missing edge is
         * not, and deleting it invites the same wrong claim to be re-added next week.
         */
        @Column(name = "disputed", nullable = false)
        private boolean disputed;

        @Column(name = "contributed_by")
        private UUID contributedBy;

        public boolean isVerified() {
            return verifiedAt != null;
        }
    }
}
