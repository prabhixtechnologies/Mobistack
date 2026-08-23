package com.fixflow.catalog.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "compatibility_group_devices")
public class CompatibilityGroupDevice extends BaseEntity {

    @Column(name = "compatibility_group_id", nullable = false)
    private UUID compatibilityGroupId;

    @Column(name = "device_model_id", nullable = false)
    private UUID deviceModelId;

    /** The model the group is named after; listed first in search results. */
    @Column(name = "primary_device", nullable = false)
    private boolean primaryDevice;

    @Column(name = "note", length = 255)
    private String note;
}
