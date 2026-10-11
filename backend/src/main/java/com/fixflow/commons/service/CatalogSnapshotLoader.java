package com.fixflow.commons.service;

import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogDeviceAlias;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.repository.CatalogBrandRepository;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceAliasRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.commons.repository.CatalogFitmentRepository;
import com.fixflow.commons.service.CatalogSnapshotDocument.BrandRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.ComponentRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.DeviceRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.FitmentRow;
import com.fixflow.group.domain.SharingGroup;
import com.fixflow.group.repository.SharingGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only catalog read against Postgres. One pass, every ten minutes, on the instance that holds
 * the refresh lock.
 */
@Service
@RequiredArgsConstructor
public class CatalogSnapshotLoader {

    private final SharingGroupRepository groups;
    private final CatalogBrandRepository brands;
    private final CatalogDeviceRepository devices;
    private final CatalogDeviceAliasRepository aliases;
    private final CatalogComponentRepository components;
    private final CatalogFitmentRepository fitments;

    @Transactional(readOnly = true)
    public Map<UUID, CatalogSnapshotDocument> load() {
        Map<UUID, List<String>> aliasByDevice = new HashMap<>();
        for (CatalogDeviceAlias alias : aliases.findAll()) {
            if (alias.getDeviceId() == null || alias.getAlias() == null) {
                continue;
            }
            aliasByDevice.computeIfAbsent(alias.getDeviceId(), id -> new ArrayList<>()).add(alias.getAlias());
        }

        Map<UUID, List<BrandRow>> brandsByGroup = new HashMap<>();
        for (CatalogBrand brand : brands.findAll()) {
            if (brand.getGroupId() == null || brand.getId() == null) {
                continue;
            }
            brandsByGroup.computeIfAbsent(brand.getGroupId(), id -> new ArrayList<>())
                    .add(new BrandRow(brand.getId(), brand.getName(), brand.getLogoUrl()));
        }

        Map<UUID, List<DeviceRow>> devicesByGroup = new HashMap<>();
        for (CatalogDevice device : devices.findAll()) {
            if (device.getGroupId() == null || device.getId() == null) {
                continue;
            }
            devicesByGroup.computeIfAbsent(device.getGroupId(), id -> new ArrayList<>())
                    .add(new DeviceRow(
                            device.getId(),
                            device.getBrandId(),
                            device.getName(),
                            device.getVariant(),
                            device.getModelCode(),
                            device.getReleaseYear(),
                            device.getLookupCount(),
                            aliasByDevice.getOrDefault(device.getId(), List.of())));
        }

        Map<UUID, List<ComponentRow>> componentsByGroup = new HashMap<>();
        for (CatalogComponent component : components.findAll()) {
            if (component.getGroupId() == null || component.getId() == null) {
                continue;
            }
            componentsByGroup.computeIfAbsent(component.getGroupId(), id -> new ArrayList<>())
                    .add(new ComponentRow(
                            component.getId(),
                            component.getCategoryCode(),
                            component.getName(),
                            component.getDescription(),
                            attributesOf(component)));
        }

        Map<UUID, List<FitmentRow>> fitmentsByGroup = new HashMap<>();
        for (CatalogFitment fitment : fitments.findAll()) {
            if (fitment.getGroupId() == null || fitment.getId() == null) {
                continue;
            }
            fitmentsByGroup.computeIfAbsent(fitment.getGroupId(), id -> new ArrayList<>())
                    .add(new FitmentRow(
                            fitment.getId(),
                            fitment.getComponentId(),
                            fitment.getDeviceId(),
                            fitment.getFitQuality() == null ? null : fitment.getFitQuality().name(),
                            fitment.getConfirmations(),
                            fitment.getDisputes(),
                            fitment.isDisputed(),
                            fitment.isVerified()));
        }

        Set<UUID> groupIds = new LinkedHashSet<>();
        for (SharingGroup group : groups.findAll()) {
            if (group.getId() != null) {
                groupIds.add(group.getId());
            }
        }
        groupIds.addAll(brandsByGroup.keySet());
        groupIds.addAll(devicesByGroup.keySet());
        groupIds.addAll(componentsByGroup.keySet());
        groupIds.addAll(fitmentsByGroup.keySet());

        Map<UUID, CatalogSnapshotDocument> documents = new LinkedHashMap<>();
        for (UUID groupId : groupIds) {
            documents.put(groupId, new CatalogSnapshotDocument(
                    brandsByGroup.getOrDefault(groupId, List.of()),
                    devicesByGroup.getOrDefault(groupId, List.of()),
                    componentsByGroup.getOrDefault(groupId, List.of()),
                    fitmentsByGroup.getOrDefault(groupId, List.of())));
        }
        return documents;
    }

    private static Map<String, Object> attributesOf(CatalogComponent component) {
        if (component.getAttributes() == null || component.getAttributes().isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(component.getAttributes());
    }
}
