package com.fixflow.updates.service;

import com.fixflow.config.FixFlowProperties;
import com.fixflow.updates.domain.AppRelease;
import com.fixflow.updates.repository.AppReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppReleaseService {

    public record ReleasePolicy(
            String platform,
            int minNativeBuild,
            int latestNativeBuild,
            boolean forceNativeUpdate,
            boolean updateRequired,
            String otaChannel,
            String otaRuntimeVersion,
            String otaUrl,
            String storeUrl,
            String notes,
            String publicOrigin
    ) {
    }

    public record ReleaseUpdate(
            Integer minNativeBuild,
            Integer latestNativeBuild,
            Boolean forceNativeUpdate,
            String otaChannel,
            String otaRuntimeVersion,
            String storeUrl,
            String notes
    ) {
    }

    private final AppReleaseRepository releaseRepository;
    private final FixFlowProperties properties;

    @Cacheable(cacheNames = "app-release", key = "#platform.toUpperCase()")
    @Transactional(readOnly = true)
    public ReleasePolicy policy(String platform, Integer currentBuild) {
        String code = platform == null ? "WEB" : platform.trim().toUpperCase();
        AppRelease row = releaseRepository.findByPlatform(code).orElseGet(() -> defaults(code));
        boolean required = row.isForceNativeUpdate()
                || (currentBuild != null && currentBuild < row.getMinNativeBuild());
        String store = row.getStoreUrl();
        if (store == null || store.isBlank()) {
            store = "IOS".equals(code) ? properties.getUpdates().getIosDownloadUrl()
                    : "ANDROID".equals(code) ? properties.getUpdates().getAndroidDownloadUrl()
                    : properties.getPlatform().getPublicOrigin();
        }
        return new ReleasePolicy(code, row.getMinNativeBuild(), row.getLatestNativeBuild(),
                row.isForceNativeUpdate(), required, row.getOtaChannel(), row.getOtaRuntimeVersion(),
                properties.getUpdates().getExpoUpdatesUrl(), store, row.getNotes(),
                properties.getPlatform().getPublicOrigin());
    }

    @Transactional(readOnly = true)
    public List<ReleasePolicy> all() {
        return List.of(policy("ANDROID", null), policy("IOS", null), policy("WEB", null));
    }

    @CacheEvict(cacheNames = "app-release", allEntries = true)
    @Transactional
    public ReleasePolicy update(String platform, ReleaseUpdate body) {
        String code = platform.trim().toUpperCase();
        AppRelease row = releaseRepository.findByPlatform(code).orElseGet(() -> defaults(code));
        if (body.minNativeBuild() != null) {
            row.setMinNativeBuild(body.minNativeBuild());
        }
        if (body.latestNativeBuild() != null) {
            row.setLatestNativeBuild(body.latestNativeBuild());
        }
        if (body.forceNativeUpdate() != null) {
            row.setForceNativeUpdate(body.forceNativeUpdate());
        }
        if (body.otaChannel() != null) {
            row.setOtaChannel(body.otaChannel());
        }
        if (body.otaRuntimeVersion() != null) {
            row.setOtaRuntimeVersion(body.otaRuntimeVersion());
        }
        if (body.storeUrl() != null) {
            row.setStoreUrl(body.storeUrl());
        }
        if (body.notes() != null) {
            row.setNotes(body.notes());
        }
        row.setUpdatedAt(Instant.now());
        releaseRepository.save(row);
        return policy(code, null);
    }

    private AppRelease defaults(String platform) {
        AppRelease row = new AppRelease();
        row.setPlatform(platform);
        if ("ANDROID".equals(platform)) {
            row.setStoreUrl(properties.getUpdates().getAndroidDownloadUrl());
        } else if ("IOS".equals(platform)) {
            row.setStoreUrl(properties.getUpdates().getIosDownloadUrl());
        } else {
            row.setStoreUrl(properties.getPlatform().getPublicOrigin());
        }
        return row;
    }
}
