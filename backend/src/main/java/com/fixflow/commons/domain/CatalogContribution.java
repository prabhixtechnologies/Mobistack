package com.fixflow.commons.domain;

import com.fixflow.common.domain.AuditableEntity;
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
 * A proposed change to the commons.
 *
 * <p>One table with a JSON payload rather than a parallel "proposed" copy of every catalog table. The
 * review queue's job is identical whatever is being proposed — read it, judge it, apply or reject it —
 * and a second set of tables would drift from the first the first time a column is added to either.
 */
@Getter
@Setter
@Entity
@Table(name = "catalog_contributions")
public class CatalogContribution extends AuditableEntity {

    public enum Kind {
        ADD_DEVICE,
        ADD_COMPONENT,
        ADD_FITMENT,
        /**
         * This part does not fit, whatever the graph says.
         *
         * <p>Always queues for review, even from a trusted contributor. A dispute removes information
         * other people are relying on, so it is the one action where being wrong is worse than being
         * slow.
         */
        DISPUTE_FITMENT,
        /**
         * This part did fit, in practice.
         *
         * <p>Applies immediately for anyone not banned. It only increments a counter — the cheapest
         * possible signal to collect, and the one most likely to be offered by someone who will not
         * fill in a form.
         */
        CONFIRM_FITMENT
    }

    public enum Status {
        PENDING,
        APPLIED,
        REJECTED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private Kind kind;

    /** Null when the proposal creates something; set when it changes or disputes an existing row. */
    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload = Map.of();

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    /** The fitment group this proposal writes into. Null on rows filed before groups existed. */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** What the proposal produced when it applied, so an accepted contribution traces to its row. */
    @Column(name = "applied_id")
    private UUID appliedId;
}
