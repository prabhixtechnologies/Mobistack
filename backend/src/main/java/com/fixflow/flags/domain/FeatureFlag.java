package com.fixflow.flags.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private boolean enabled;
}
