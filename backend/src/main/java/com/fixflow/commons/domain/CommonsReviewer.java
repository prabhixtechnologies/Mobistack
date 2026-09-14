package com.fixflow.commons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who may settle disputes in the shared catalog.
 *
 * <p>Not a shop role. {@code COMPATIBILITY_APPROVE} is about one shop's private groups;
 * a row here changes what every other shop reads, so Prabhix grants it per user.
 */
@Getter
@Setter
@Entity
@Table(name = "commons_reviewers")
public class CommonsReviewer {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "reason", length = 500)
    private String reason;
}
