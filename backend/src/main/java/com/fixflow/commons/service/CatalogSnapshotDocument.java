package com.fixflow.commons.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One fitment group's catalog as it is stored in Redis. Built by the refresh job and read back
 * into {@link CatalogGroupSnapshot}. Request threads never assemble this from the database.
 */
public record CatalogSnapshotDocument(List<BrandRow> brands,
                                      List<DeviceRow> devices,
                                      List<ComponentRow> components,
                                      List<FitmentRow> fitments) {

    public CatalogSnapshotDocument {
        brands = brands == null ? List.of() : List.copyOf(brands);
        devices = devices == null ? List.of() : List.copyOf(devices);
        components = components == null ? List.of() : List.copyOf(components);
        fitments = fitments == null ? List.of() : List.copyOf(fitments);
    }

    public record BrandRow(UUID id, String name, String logoUrl) {
    }

    public record DeviceRow(UUID id,
                            UUID brandId,
                            String name,
                            String variant,
                            String modelCode,
                            Integer releaseYear,
                            long lookupCount,
                            List<String> aliases) {
        public DeviceRow {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    public record ComponentRow(UUID id,
                               String categoryCode,
                               String name,
                               String description,
                               Map<String, Object> attributes) {
        public ComponentRow {
            attributes = attributes == null || attributes.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }

    public record FitmentRow(UUID id,
                             UUID componentId,
                             UUID deviceId,
                             String fitQuality,
                             int confirmations,
                             int disputes,
                             boolean disputed,
                             boolean verified) {
    }
}
