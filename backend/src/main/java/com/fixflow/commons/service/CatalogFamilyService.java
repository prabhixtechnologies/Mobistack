package com.fixflow.commons.service;

import com.fixflow.catalog.domain.DefaultCategories;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.domain.CatalogEntities.FitQuality;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.commons.repository.CatalogFitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One spare identity and the phones that take it. Compatible models are the other devices on the
 * same component — there is no separate equivalence table.
 */
@Service
@RequiredArgsConstructor
public class CatalogFamilyService {

    private final CommonsCatalogService catalog;
    private final CatalogComponentRepository components;
    private final CatalogFitmentRepository fitments;
    private final CatalogDeviceRepository devices;
    private final CatalogCache cache;

    @Transactional
    public FamilyView create(UUID groupId,
                             String categoryCode,
                             String name,
                             List<UUID> deviceIds,
                             UUID actorId) {
        if (deviceIds == null || deviceIds.size() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Name at least two models that share this part.");
        }
        CatalogComponent component = catalog.addComponent(
                categoryCode, name, null, Map.of(), actorId, groupId);
        for (UUID deviceId : deviceIds) {
            catalog.addFitment(component.getId(), deviceId, FitQuality.COMPATIBLE, actorId, groupId);
        }
        cache.evictGroupAfterCommit(groupId);
        return familyOf(groupId, component);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CatalogCache.NAME, key = "#groupId + ':categories'")
    public List<CategoryIndex> categories(UUID groupId) {
        catalog.requireGroup(groupId);
        Map<String, Long> families = components.countByCategory(groupId).stream()
                .collect(Collectors.toMap(CatalogComponentRepository.CategoryCount::getCategoryCode,
                        CatalogComponentRepository.CategoryCount::getFamilyCount));
        Map<String, Long> phones = fitments.countDevicesByCategory(groupId).stream()
                .collect(Collectors.toMap(CatalogFitmentRepository.CategoryCount::getCategoryCode,
                        CatalogFitmentRepository.CategoryCount::getDeviceCount));
        List<CategoryIndex> rows = new ArrayList<>();
        for (DefaultCategories.Template template : DefaultCategories.ALL) {
            if (!template.compatibilityRelevant() && !families.containsKey(template.code())) {
                continue;
            }
            rows.add(new CategoryIndex(
                    template.code(),
                    template.name(),
                    template.sortOrder(),
                    families.getOrDefault(template.code(), 0L),
                    phones.getOrDefault(template.code(), 0L)));
        }
        for (Map.Entry<String, Long> extra : families.entrySet()) {
            boolean known = rows.stream().anyMatch(row -> row.code().equals(extra.getKey()));
            if (!known) {
                rows.add(new CategoryIndex(extra.getKey(), labelOf(extra.getKey()), 500,
                        extra.getValue(), phones.getOrDefault(extra.getKey(), 0L)));
            }
        }
        rows.sort(Comparator.comparingInt(CategoryIndex::sortOrder));
        return rows;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CatalogCache.NAME, key = "#groupId + ':families:' + #categoryCode")
    public List<FamilyView> familiesInCategory(UUID groupId, String categoryCode) {
        catalog.requireGroup(groupId);
        String code = requiredCode(categoryCode);
        List<CatalogComponent> found = components.findByGroupIdAndCategoryCodeOrderByNameAsc(groupId, code);
        return found.stream().map(component -> familyOf(groupId, component)).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CatalogCache.NAME,
            key = "#groupId + ':family:' + #deviceId + ':' + (#categoryCode == null ? '' : #categoryCode)")
    public List<FamilyView> forDevice(UUID groupId, UUID deviceId, String categoryCode) {
        catalog.requireDevice(groupId, deviceId);
        String code = categoryCode == null || categoryCode.isBlank() ? null : requiredCode(categoryCode);
        List<CatalogFitment> edges = fitments.findForDevice(groupId, deviceId);
        Map<UUID, CatalogComponent> byId = components
                .findAllById(edges.stream().map(CatalogFitment::getComponentId).toList())
                .stream()
                .collect(Collectors.toMap(CatalogComponent::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        List<FamilyView> rows = new ArrayList<>();
        for (CatalogFitment edge : edges) {
            CatalogComponent component = byId.get(edge.getComponentId());
            if (component == null) {
                continue;
            }
            if (code != null && !code.equals(component.getCategoryCode())) {
                continue;
            }
            rows.add(familyOf(groupId, component));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CatalogCache.NAME, key = "#groupId + ':brands'")
    public List<BrandIndex> brands(UUID groupId) {
        Map<UUID, Long> counts = devices.countByBrand(groupId).stream()
                .collect(Collectors.toMap(CatalogDeviceRepository.BrandCount::getBrandId,
                        CatalogDeviceRepository.BrandCount::getDeviceCount));
        return catalog.listBrands(groupId).stream()
                .map(brand -> new BrandIndex(brand.getId(), brand.getName(), brand.getLogoUrl(),
                        counts.getOrDefault(brand.getId(), 0L)))
                .toList();
    }

    private FamilyView familyOf(UUID groupId, CatalogComponent component) {
        List<CatalogFitment> edges = fitments.findForComponent(groupId, component.getId());
        List<UUID> deviceIds = edges.stream().map(CatalogFitment::getDeviceId).toList();
        Map<UUID, CatalogFitment> edgeByDevice = edges.stream()
                .collect(Collectors.toMap(CatalogFitment::getDeviceId, e -> e, (a, b) -> a));
        List<CatalogDevice> members = deviceIds.isEmpty() ? List.of() : devices.findAllById(deviceIds);
        Map<UUID, String> brandNames = catalog.brandNames(members.stream()
                .map(CatalogDevice::getBrandId).distinct().toList());
        List<MemberView> memberViews = members.stream()
                .sorted(Comparator.comparing((CatalogDevice d) -> brandNames.getOrDefault(d.getBrandId(), ""))
                        .thenComparing(CatalogDevice::getName))
                .map(device -> {
                    CatalogFitment edge = edgeByDevice.get(device.getId());
                    return new MemberView(
                            device.getId(),
                            brandNames.get(device.getBrandId()),
                            device.getName(),
                            device.getVariant(),
                            device.getModelCode(),
                            edge == null ? FitQuality.COMPATIBLE.name() : edge.getFitQuality().name(),
                            edge != null && edge.isVerified(),
                            edge != null && edge.isDisputed(),
                            edge == null ? null : edge.getId());
                })
                .toList();
        return new FamilyView(
                component.getId(),
                component.getCategoryCode(),
                labelOf(component.getCategoryCode()),
                component.getName(),
                component.getDescription(),
                memberViews);
    }

    private static String requiredCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A family needs a part type.");
        }
        return categoryCode.trim().toUpperCase();
    }

    private static String labelOf(String code) {
        return DefaultCategories.ALL.stream()
                .filter(template -> template.code().equals(code))
                .map(DefaultCategories.Template::name)
                .findFirst()
                .orElse(code.replace('_', ' '));
    }

    public record CategoryIndex(String code, String name, int sortOrder, long familyCount, long deviceCount) {
    }

    public record BrandIndex(UUID id, String name, String logoUrl, long deviceCount) {
    }

    public record MemberView(UUID id,
                             String brandName,
                             String name,
                             String variant,
                             String modelCode,
                             String fit,
                             boolean verified,
                             boolean disputed,
                             UUID fitmentId) {
    }

    public record FamilyView(UUID id,
                             String categoryCode,
                             String categoryName,
                             String name,
                             String description,
                             List<MemberView> members) {
    }
}
