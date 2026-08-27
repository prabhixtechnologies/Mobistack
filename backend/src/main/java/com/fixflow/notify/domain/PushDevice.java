package com.fixflow.notify.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "push_devices")
public class PushDevice extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "device_id", nullable = false, length = 80)
    private String deviceId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "expo_push_token", length = 240)
    private String expoPushToken;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "native_build")
    private Integer nativeBuild;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    /** Why Expo last refused this token, kept so support can explain silence. */
    @Column(name = "last_error", length = 200)
    private String lastError;

    /**
     * Set once Expo reports the token is dead — the app was uninstalled, or the
     * registration was replaced. Retired rows are skipped rather than retried.
     */
    @Column(name = "retired_at")
    private Instant retiredAt;
}
