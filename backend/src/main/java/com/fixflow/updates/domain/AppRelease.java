package com.fixflow.updates.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "app_releases")
public class AppRelease {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "platform", nullable = false, unique = true, length = 20)
    private String platform;

    @Column(name = "min_native_build", nullable = false)
    private int minNativeBuild = 1;

    @Column(name = "latest_native_build", nullable = false)
    private int latestNativeBuild = 1;

    @Column(name = "ota_channel", nullable = false, length = 40)
    private String otaChannel = "production";

    @Column(name = "ota_runtime_version", length = 40)
    private String otaRuntimeVersion;

    @Column(name = "force_native_update", nullable = false)
    private boolean forceNativeUpdate;

    @Column(name = "store_url", length = 400)
    private String storeUrl;

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
