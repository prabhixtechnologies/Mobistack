package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogDeviceAlias;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.domain.CatalogEntities.FitQuality;
import com.fixflow.commons.service.CatalogFamilyService.BrandIndex;
import com.fixflow.commons.service.CatalogFamilyService.CategoryIndex;
import com.fixflow.commons.service.CatalogFamilyService.FamilyView;
import com.fixflow.commons.service.CatalogSnapshotDocument.BrandRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.ComponentRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.DeviceRow;
import com.fixflow.commons.service.CatalogSnapshotDocument.FitmentRow;
import com.fixflow.commons.service.CommonsCatalogService.FitmentView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One published copy of a fitment group. Search, spare types, brands and phone compatibility are
 * answered from these indexes. The database is not consulted.
 */
public final class CatalogGroupSnapshot {

    /** Matches {@code CommonsCatalogService}'s page cap. */
    private static final int MAX_PAGE = 200;

    /** Matches {@code CatalogFamilyService}'s family page cap. */
    private static final int MAX_FAMILIES = 40;

    private static final Comparator<CatalogFitment> FIT_ORDER = Comparator
            .comparing(CatalogFitment::isDisputed)
            .thenComparing(Comparator.comparingInt(CatalogFitment::getConfirmations).reversed());

    private final List<CatalogBrand> brands;
    private final Map<UUID, CatalogBrand> brandsById;
    private final List<CatalogDevice> devices;
    private final Map<UUID, CatalogDevice> devicesById;
    private final Map<UUID, List<String>> aliasesByDevice;
    private final Map<UUID, CatalogComponent> componentsById;
    private final List<CatalogComponent> components;
    private final Map<UUID, List<CatalogFitment>> fitmentsByDevice;
    private final Map<UUID, List<CatalogFitment>> fitmentsByComponent;
    private final Map<UUID, String> brandNames;

    private CatalogGroupSnapshot(List<CatalogBrand> brands,
                                 Map<UUID, CatalogBrand> brandsById,
                                 List<CatalogDevice> devices,
                                 Map<UUID, CatalogDevice> devicesById,
                                 Map<UUID, List<String>> aliasesByDevice,
                                 Map<UUID, CatalogComponent> componentsById,
                                 List<CatalogComponent> components,
                                 Map<UUID, List<CatalogFitment>> fitmentsByDevice,
                                 Map<UUID, List<CatalogFitment>> fitmentsByComponent,
                                 Map<UUID, String> brandNames) {
        this.brands = brands;
        this.brandsById = brandsById;
        this.devices = devices;
        this.devicesById = devicesById;
        this.aliasesByDevice = aliasesByDevice;
        this.componentsById = componentsById;
        this.components = components;
        this.fitmentsByDevice = fitmentsByDevice;
        this.fitmentsByComponent = fitmentsByComponent;
        this.brandNames = brandNames;
    }

    public static CatalogGroupSnapshot empty(UUID groupId) {
        return from(groupId, new CatalogSnapshotDocument(List.of(), List.of(), List.of(), List.of()));
    }

    public static CatalogGroupSnapshot from(UUID groupId, CatalogSnapshotDocument document) {
        CatalogSnapshotDocument source = document == null
                ? new CatalogSnapshotDocument(List.of(), List.of(), List.of(), List.of())
                : document;
        Map<UUID, CatalogBrand> brandsById = new LinkedHashMap<>();
        for (BrandRow row : source.brands()) {
            if (row.id() == null) {
                continue;
            }
            CatalogBrand brand = new CatalogBrand();
            brand.setId(row.id());
            brand.setGroupId(groupId);
            brand.setName(row.name());
            brand.setLogoUrl(row.logoUrl());
            brandsById.put(brand.getId(), brand);
        }
        List<CatalogBrand> brands = new ArrayList<>(brandsById.values());
        brands.sort(Comparator.comparing(CatalogBrand::getName, Comparator.nullsLast(String::compareTo)));

        Map<UUID, List<String>> aliasesByDevice = new HashMap<>();
        Map<UUID, CatalogDevice> devicesById = new LinkedHashMap<>();
        for (DeviceRow row : source.devices()) {
            if (row.id() == null) {
                continue;
            }
            CatalogDevice device = new CatalogDevice();
            device.setId(row.id());
            device.setGroupId(groupId);
            device.setBrandId(row.brandId());
            device.setName(row.name());
            device.setVariant(row.variant());
            device.setModelCode(row.modelCode());
            device.setReleaseYear(row.releaseYear());
            device.setLookupCount(row.lookupCount());
            devicesById.put(device.getId(), device);
            aliasesByDevice.put(device.getId(), row.aliases());
        }

        Map<UUID, CatalogComponent> componentsById = new LinkedHashMap<>();
        for (ComponentRow row : source.components()) {
            if (row.id() == null) {
                continue;
            }
            CatalogComponent component = new CatalogComponent();
            component.setId(row.id());
            component.setGroupId(groupId);
            component.setCategoryCode(row.categoryCode());
            component.setName(row.name());
            component.setDescription(row.description());
            component.setAttributes(row.attributes());
            componentsById.put(component.getId(), component);
        }
        List<CatalogComponent> components = new ArrayList<>(componentsById.values());
        components.sort(Comparator.comparing(CatalogComponent::getName, Comparator.nullsLast(String::compareTo)));

        Map<UUID, List<CatalogFitment>> fitmentsByDevice = new HashMap<>();
        Map<UUID, List<CatalogFitment>> fitmentsByComponent = new HashMap<>();
        for (FitmentRow row : source.fitments()) {
            if (row.id() == null || row.deviceId() == null || row.componentId() == null) {
                continue;
            }
            CatalogFitment fitment = new CatalogFitment();
            fitment.setId(row.id());
            fitment.setGroupId(groupId);
            fitment.setComponentId(row.componentId());
            fitment.setDeviceId(row.deviceId());
            fitment.setFitQuality(quality(row.fitQuality()));
            fitment.setConfirmations(row.confirmations());
            fitment.setDisputes(row.disputes());
            fitment.setDisputed(row.disputed());
            if (row.verified()) {
                fitment.setVerifiedAt(Instant.EPOCH);
            }
            fitmentsByDevice.computeIfAbsent(row.deviceId(), id -> new ArrayList<>()).add(fitment);
            fitmentsByComponent.computeIfAbsent(row.componentId(), id -> new ArrayList<>()).add(fitment);
        }
        fitmentsByDevice.values().forEach(edges -> edges.sort(FIT_ORDER));
        fitmentsByComponent.values().forEach(edges -> edges.sort(FIT_ORDER));

        Map<UUID, String> brandNames = new HashMap<>();
        brandsById.forEach((id, brand) -> {
            if (brand.getName() != null) {
                brandNames.put(id, brand.getName());
            }
        });
        return new CatalogGroupSnapshot(
                List.copyOf(brands),
                Map.copyOf(brandsById),
                List.copyOf(devicesById.values()),
                Map.copyOf(devicesById),
                Map.copyOf(aliasesByDevice),
                Map.copyOf(componentsById),
                List.copyOf(components),
                copyEdges(fitmentsByDevice),
                copyEdges(fitmentsByComponent),
                Map.copyOf(brandNames));
    }

    public long brandCount() {
        return brands.size();
    }

    public long deviceCount() {
        return devices.size();
    }

    public long componentCount() {
        return components.size();
    }

    public long fitmentCount() {
        return fitmentsByDevice.values().stream().mapToLong(List::size).sum();
    }

    public List<CatalogBrand> listBrands() {
        return brands;
    }

    public Map<UUID, String> brandNames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID id : ids) {
            String name = brandNames.get(id);
            if (name != null) {
                names.put(id, name);
            }
        }
        return names;
    }

    public List<CategoryIndex> categories() {
        return CatalogFamilyService.categoryIndex(devices.size(), familyCounts(), phoneCounts());
    }

    public List<BrandIndex> brands() {
        Map<UUID, Long> counts = totalsByBrand();
        return brands.stream()
                .map(brand -> new BrandIndex(brand.getId(), brand.getName(), brand.getLogoUrl(),
                        counts.getOrDefault(brand.getId(), 0L),
                        counts.getOrDefault(brand.getId(), 0L)))
                .toList();
    }

    public List<BrandIndex> brandsInCategory(String categoryCode) {
        String code = CatalogFamilyService.requiredCode(categoryCode);
        Map<UUID, Long> totals = totalsByBrand();
        Map<UUID, Long> mapped = mappedByBrand(code);
        return brands.stream()
                .map(brand -> new BrandIndex(brand.getId(), brand.getName(), brand.getLogoUrl(),
                        totals.getOrDefault(brand.getId(), 0L),
                        mapped.getOrDefault(brand.getId(), 0L)))
                .toList();
    }

    public Page<CatalogDevice> searchDevices(String term, int page, int size) {
        String needle = checkedTerm(term);
        List<CatalogDevice> matched = devices.stream()
                .filter(device -> contains(device.getName(), needle) || aliasContains(device.getId(), needle))
                .sorted(Comparator.comparingLong(CatalogDevice::getLookupCount).reversed()
                        .thenComparing(CatalogDevice::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return page(matched, page, size, MAX_PAGE);
    }

    public Page<CatalogDevice> listDevices(int page, int size) {
        List<CatalogDevice> ordered = devices.stream()
                .sorted(Comparator.comparingLong(CatalogDevice::getLookupCount).reversed()
                        .thenComparing(CatalogDevice::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return page(ordered, page, size, MAX_PAGE);
    }

    public List<CatalogDevice> popular(int limit) {
        return listDevices(0, limit).getContent();
    }

    /**
     * The spare type is navigation context. Phones without a recorded family stay in the brand list.
     */
    public Page<CatalogDevice> devicesForBrand(UUID brandId, int page, int size) {
        requireBrand(brandId);
        List<CatalogDevice> matched = devices.stream()
                .filter(device -> brandId.equals(device.getBrandId()))
                .sorted(Comparator.comparing(CatalogDevice::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return page(matched, page, size, MAX_PAGE);
    }

    public Page<CatalogDevice> searchInBrand(UUID brandId, String term, int page, int size) {
        requireBrand(brandId);
        String needle = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        List<CatalogDevice> matched = devices.stream()
                .filter(device -> brandId.equals(device.getBrandId()))
                .filter(device -> needle.isEmpty()
                        || contains(device.getName(), needle)
                        || contains(device.getVariant(), needle)
                        || contains(device.getModelCode(), needle)
                        || aliasContains(device.getId(), needle))
                .sorted(Comparator.comparing(CatalogDevice::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return page(matched, page, size, MAX_PAGE);
    }

    public CatalogDevice device(UUID deviceId) {
        CatalogDevice device = devicesById.get(deviceId);
        if (device == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such device");
        }
        return device;
    }

    public CatalogComponent component(UUID componentId) {
        CatalogComponent component = componentsById.get(componentId);
        if (component == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such component");
        }
        return component;
    }

    public Page<CatalogComponent> listComponents(int page, int size) {
        return page(components, page, size, MAX_PAGE);
    }

    public Page<CatalogComponent> searchComponents(String term, int page, int size) {
        String needle = checkedTerm(term);
        List<CatalogComponent> matched = components.stream()
                .filter(component -> contains(component.getName(), needle))
                .toList();
        return page(matched, page, size, MAX_PAGE);
    }

    public List<FitmentView> fitmentsForDevice(UUID deviceId) {
        CatalogDevice device = device(deviceId);
        return fitmentsByDevice.getOrDefault(deviceId, List.of()).stream()
                .map(edge -> new FitmentView(edge, componentsById.get(edge.getComponentId()), device))
                .toList();
    }

    public List<CatalogDevice> devicesForComponent(UUID componentId) {
        component(componentId);
        List<CatalogDevice> found = new ArrayList<>();
        for (CatalogFitment edge : fitmentsByComponent.getOrDefault(componentId, List.of())) {
            CatalogDevice device = devicesById.get(edge.getDeviceId());
            if (device != null) {
                found.add(device);
            }
        }
        return List.copyOf(found);
    }

    public List<CatalogDeviceAlias> aliasesFor(UUID deviceId) {
        return aliasesByDevice.getOrDefault(deviceId, List.of()).stream()
                .map(alias -> {
                    CatalogDeviceAlias row = new CatalogDeviceAlias();
                    row.setDeviceId(deviceId);
                    row.setAlias(alias);
                    return row;
                })
                .toList();
    }

    public Page<FamilyView> familiesInCategory(String categoryCode, int page, int size) {
        String code = CatalogFamilyService.requiredCode(categoryCode);
        List<CatalogComponent> matched = components.stream()
                .filter(component -> code.equals(component.getCategoryCode()))
                .toList();
        Page<CatalogComponent> slice = page(matched, page, size, MAX_FAMILIES);
        List<FamilyView> rows = slice.getContent().stream().map(this::familyOf).toList();
        return new PageImpl<>(rows, slice.getPageable(), slice.getTotalElements());
    }

    public List<FamilyView> forDevice(UUID deviceId, String categoryCode) {
        device(deviceId);
        String code = categoryCode == null || categoryCode.isBlank()
                ? null
                : CatalogFamilyService.requiredCode(categoryCode);
        Map<UUID, CatalogComponent> used = new LinkedHashMap<>();
        List<FamilyView> rows = new ArrayList<>();
        for (CatalogFitment edge : fitmentsByDevice.getOrDefault(deviceId, List.of())) {
            CatalogComponent component = componentsById.get(edge.getComponentId());
            if (component == null || used.containsKey(component.getId())) {
                continue;
            }
            if (code != null && !code.equals(component.getCategoryCode())) {
                continue;
            }
            used.put(component.getId(), component);
            rows.add(familyOf(component));
        }
        return CatalogFamilyService.withoutCoveredDuplicates(rows, used);
    }

    private FamilyView familyOf(CatalogComponent component) {
        List<CatalogFitment> edges = fitmentsByComponent.getOrDefault(component.getId(), List.of());
        Map<UUID, CatalogDevice> members = new LinkedHashMap<>();
        for (CatalogFitment edge : edges) {
            CatalogDevice device = devicesById.get(edge.getDeviceId());
            if (device != null) {
                members.put(device.getId(), device);
            }
        }
        return CatalogFamilyService.toFamily(component, edges, members, brandNames);
    }

    private Map<String, Long> familyCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (CatalogComponent component : components) {
            if (component.getCategoryCode() == null) {
                continue;
            }
            counts.merge(component.getCategoryCode(), 1L, Long::sum);
        }
        return counts;
    }

    private Map<String, Long> phoneCounts() {
        Map<String, Set<UUID>> phones = new HashMap<>();
        for (CatalogComponent component : components) {
            if (component.getCategoryCode() == null) {
                continue;
            }
            Set<UUID> ids = phones.computeIfAbsent(component.getCategoryCode(), code -> new HashSet<>());
            for (CatalogFitment edge : fitmentsByComponent.getOrDefault(component.getId(), List.of())) {
                if (edge.getDeviceId() != null) {
                    ids.add(edge.getDeviceId());
                }
            }
        }
        Map<String, Long> counts = new HashMap<>();
        phones.forEach((code, ids) -> counts.put(code, (long) ids.size()));
        return counts;
    }

    private Map<UUID, Long> totalsByBrand() {
        Map<UUID, Long> counts = new HashMap<>();
        for (CatalogDevice device : devices) {
            if (device.getBrandId() != null) {
                counts.merge(device.getBrandId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<UUID, Long> mappedByBrand(String categoryCode) {
        Set<UUID> mappedDevices = new HashSet<>();
        for (CatalogComponent component : components) {
            if (!categoryCode.equals(component.getCategoryCode())) {
                continue;
            }
            for (CatalogFitment edge : fitmentsByComponent.getOrDefault(component.getId(), List.of())) {
                if (edge.getDeviceId() != null) {
                    mappedDevices.add(edge.getDeviceId());
                }
            }
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (CatalogDevice device : devices) {
            if (device.getBrandId() != null && mappedDevices.contains(device.getId())) {
                counts.merge(device.getBrandId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private void requireBrand(UUID brandId) {
        if (brandId == null || !brandsById.containsKey(brandId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such brand");
        }
    }

    private boolean aliasContains(UUID deviceId, String needle) {
        for (String alias : aliasesByDevice.getOrDefault(deviceId, List.of())) {
            if (contains(alias, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String checkedTerm(String term) {
        if (term == null || term.trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Type at least two characters to search.");
        }
        return term.trim().toLowerCase(Locale.ROOT);
    }

    private static FitQuality quality(String raw) {
        if (raw == null || raw.isBlank()) {
            return FitQuality.EXACT;
        }
        try {
            return FitQuality.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return FitQuality.EXACT;
        }
    }

    private static Map<UUID, List<CatalogFitment>> copyEdges(Map<UUID, List<CatalogFitment>> edges) {
        Map<UUID, List<CatalogFitment>> copy = new HashMap<>();
        edges.forEach((id, rows) -> copy.put(id, List.copyOf(rows)));
        return Map.copyOf(copy);
    }

    private static <T> Page<T> page(List<T> rows, int page, int size, int max) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), max);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        int from = safePage * safeSize;
        if (from >= rows.size()) {
            return new PageImpl<>(List.of(), pageable, rows.size());
        }
        return new PageImpl<>(List.copyOf(rows.subList(from, Math.min(from + safeSize, rows.size()))),
                pageable, rows.size());
    }
}
