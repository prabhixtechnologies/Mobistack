package com.fixflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "compatibility_history")
public class CompatibilityHistory {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 160)
    private String actorName;

    @Column(nullable = false)
    private String summary;

    @Column(length = 255)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data")
    private Map<String, Object> afterData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
