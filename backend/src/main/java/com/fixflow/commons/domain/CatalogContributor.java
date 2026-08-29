package com.fixflow.commons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Somebody's standing in the commons.
 *
 * <p>Reputation is earned, not assigned. Without it the commons has two bad options: trust everyone,
 * and it fills with wrong claims nobody can correct at scale, or trust nobody, and every contribution
 * waits on a reviewer who does not exist yet. A newcomer's edits queue; someone with a track record
 * writes directly.
 *
 * <p>Trust is granted by a reviewer rather than computed from a threshold. An automatic promotion at
 * N accepted contributions is a thing to game — submit N trivially-correct rows, then the wrong one
 * that matters.
 *
 * <p>Keyed by {@code user_id} rather than a surrogate id, so the row cannot be duplicated for one
 * person. {@link Persistable} is implemented because an assigned identifier makes Spring Data think
 * every instance is an update, and the first save of a new contributor would fail.
 */
@Getter
@Setter
@Entity
@Table(name = "catalog_contributors")
public class CatalogContributor implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "trusted", nullable = false)
    private boolean trusted;

    @Column(name = "trusted_at")
    private Instant trustedAt;

    @Column(name = "trusted_by")
    private UUID trustedBy;

    /** Refused outright. Separate from untrusted: an untrusted contributor is new, a banned one known. */
    @Column(name = "banned", nullable = false)
    private boolean banned;

    @Column(name = "banned_reason", length = 255)
    private String bannedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Set only by {@link #forUser}, so the first save inserts.
     *
     * <p>Cannot be inferred from the id, which is assigned and therefore never null; Spring Data would
     * otherwise treat every instance as an update and the first save would fail with no rows affected.
     */
    @jakarta.persistence.Transient
    private boolean fresh;

    public static CatalogContributor forUser(UUID userId) {
        CatalogContributor contributor = new CatalogContributor();
        contributor.userId = userId;
        contributor.fresh = true;
        return contributor;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }
}
