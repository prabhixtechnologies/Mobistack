package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.CompatibilityChangeRequest;
import com.fixflow.catalog.domain.CompatibilityGroup;
import com.fixflow.catalog.domain.CompatibilityGroupDevice;
import com.fixflow.catalog.domain.CompatibilityHistory;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.CategoryOverview;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupResponse;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityOverviewResponse;
import com.fixflow.catalog.dto.CatalogDtos.CopyGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupDeviceRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupDeviceResponse;
import com.fixflow.catalog.dto.CatalogDtos.GroupMembershipRequest;
import com.fixflow.catalog.repository.CategoryRepository;
import com.fixflow.catalog.repository.CompatibilityChangeRequestRepository;
import com.fixflow.catalog.repository.CompatibilityGroupDeviceRepository;
import com.fixflow.catalog.repository.CompatibilityGroupRepository;
import com.fixflow.catalog.repository.CompatibilityHistoryRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductCompatibilityRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.util.TextNormalizer;
import com.fixflow.flags.service.FeatureFlagService;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.Permission;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the "these models take the same part" lists.
 *
 * <p>This is the workflow the shop already knows from its old universal list,
 * so the API is deliberately shaped around bulk device membership rather than
 * one-row-at-a-time editing.
 */
@Service
@RequiredArgsConstructor
public class CompatibilityGroupService {

    private final CompatibilityGroupRepository groupRepository;
    private final CompatibilityGroupDeviceRepository groupDeviceRepository;
    private final DeviceModelRepository deviceModelRepository;
    private final DeviceService deviceService;
    private final CategoryRepository categoryRepository;
    private final ProductCompatibilityRepository productCompatibilityRepository;
    private final CompatibilityHistoryRepository historyRepository;
    private final CompatibilityChangeRequestRepository changeRequestRepository;
    private final ShopRepository shopRepository;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<CompatibilityGroupResponse> list(UUID shopId, UUID categoryId, String q, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase();
        Page<CompatibilityGroup> page;
        if (query == null) {
            page = categoryId == null
                    ? groupRepository.findByShopIdAndActiveTrueOrderByCreatedAtAsc(shopId, pageable)
                    : groupRepository.findByShopIdAndCategoryIdAndActiveTrueOrderByCreatedAtAsc(
                            shopId, categoryId, pageable);
        } else {
            page = categoryId == null
                    ? groupRepository.search(shopId, query, pageable)
                    : groupRepository.searchInCategory(shopId, categoryId, query, pageable);
        }
        return page.map(group -> toResponse(shopId, group));
    }

    @Transactional(readOnly = true)
    public CompatibilityOverviewResponse overview(UUID shopId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (CompatibilityGroupRepository.CategoryGroupCount row : groupRepository.countActiveByCategory(shopId)) {
            if (row.getCategoryId() != null) {
                counts.put(row.getCategoryId(), row.getGroupCount());
            }
        }
        List<CategoryOverview> categories = categoryRepository
                .findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopId)
                .stream()
                .filter(Category::isCompatibilityRelevant)
                .map(category -> new CategoryOverview(
                        category.getId(),
                        category.getCode(),
                        category.getName(),
                        category.getIcon(),
                        category.getColor(),
                        category.getSortOrder(),
                        counts.getOrDefault(category.getId(), 0L)))
                .toList();
        return new CompatibilityOverviewResponse(categories, groupRepository.countByShopIdAndActiveTrue(shopId));
    }

    @Transactional(readOnly = true)
    public CompatibilityGroupResponse get(UUID shopId, UUID id) {
        return toResponse(shopId, require(shopId, id));
    }

    @Transactional
    public CompatibilityGroupResponse create(UUID shopId, CompatibilityGroupRequest request) {
        String code = resolveCode(shopId, request);

        CompatibilityGroup group = new CompatibilityGroup();
        group.setShopId(shopId);
        group.setCode(code);
        apply(shopId, group, request);
        groupRepository.save(group);

        List<UUID> deviceIds = resolveMembershipIds(shopId, request.deviceModelIds(), request.deviceTexts());
        for (int i = 0; i < deviceIds.size(); i++) {
            addDeviceInternal(shopId, group, deviceIds.get(i), i == 0, null);
        }

        auditService.record(AuditAction.COMPATIBILITY_GROUP_CREATED, "CompatibilityGroup", group.getId(),
                "Created compatibility group \"%s\"".formatted(group.getName()));
        return toResponse(shopId, group);
    }

    @Transactional
    public CompatibilityGroupResponse update(UUID shopId, UUID id, CompatibilityGroupRequest request) {
        CompatibilityGroup group = require(shopId, id);
        apply(shopId, group, request);
        groupRepository.save(group);

        auditService.record(AuditAction.COMPATIBILITY_GROUP_UPDATED, "CompatibilityGroup", id,
                "Updated compatibility group \"%s\"".formatted(group.getName()));
        return toResponse(shopId, group);
    }

    @Transactional
    public CompatibilityGroupResponse copy(UUID shopId, UUID id, CopyGroupRequest request) {
        CompatibilityGroup source = require(shopId, id);
        UUID categoryId = request != null && request.categoryId() != null
                ? request.categoryId()
                : source.getCategoryId();
        if (categoryId != null) {
            categoryRepository.findByIdAndShopId(categoryId, shopId)
                    .orElseThrow(() -> ApiException.notFound("Category", categoryId));
        }
        String name = request != null && request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : trimName(source.getName() + " copy");

        CompatibilityGroup copy = new CompatibilityGroup();
        copy.setShopId(shopId);
        copy.setCategoryId(categoryId);
        copy.setName(name);
        copy.setCode(resolveCode(shopId, new CompatibilityGroupRequest(
                null, name, categoryId, null, false, true, null, null)));
        copy.setNotes(source.getNotes());
        copy.setVerified(false);
        copy.setActive(true);
        groupRepository.save(copy);

        for (CompatibilityGroupDevice link : groupDeviceRepository.findByCompatibilityGroupId(source.getId())) {
            CompatibilityGroupDevice clone = new CompatibilityGroupDevice();
            clone.setCompatibilityGroupId(copy.getId());
            clone.setDeviceModelId(link.getDeviceModelId());
            clone.setPrimaryDevice(link.isPrimaryDevice());
            clone.setNote(link.getNote());
            groupDeviceRepository.save(clone);
        }

        auditService.record(AuditAction.COMPATIBILITY_GROUP_COPIED, "CompatibilityGroup", copy.getId(),
                "Copied compatibility group \"%s\"".formatted(source.getName()));
        recordHistory(shopId, copy, "Copied from " + source.getName(), Map.of("sourceId", source.getId().toString()));
        return toResponse(shopId, copy);
    }

    @Transactional
    public void delete(UUID shopId, UUID id) {
        CompatibilityGroup group = require(shopId, id);
        long linked = productCompatibilityRepository.countByCompatibilityGroupId(id);
        if (linked == 0) {
            groupRepository.delete(group);
        } else {
            group.setActive(false);
            groupRepository.save(group);
        }
        auditService.record(AuditAction.COMPATIBILITY_GROUP_DELETED, "CompatibilityGroup", id,
                linked == 0
                        ? "Deleted compatibility group \"%s\"".formatted(group.getName())
                        : "Deactivated compatibility group \"%s\" (parts still linked)".formatted(group.getName()));
    }

    @Transactional
    public CompatibilityGroupResponse replaceMembership(UUID shopId, UUID groupId, GroupMembershipRequest request) {
        CompatibilityGroup group = require(shopId, groupId);
        List<UUID> desired = resolveMembershipIds(shopId,
                request == null ? null : request.deviceModelIds(),
                request == null ? null : request.deviceTexts());
        if (desired.isEmpty()) {
            throw ApiException.businessRule("A compatibility group needs at least one model.");
        }

        Set<UUID> current = groupDeviceRepository.findByCompatibilityGroupId(groupId).stream()
                .map(CompatibilityGroupDevice::getDeviceModelId)
                .collect(Collectors.toCollection(HashSet::new));

        if (requiresApproval(shopId)) {
            for (UUID deviceId : current) {
                if (!desired.contains(deviceId)) {
                    queueChange(shopId, group, CompatibilityChangeRequest.Action.REMOVE_DEVICE, deviceId, null);
                }
            }
            for (UUID deviceId : desired) {
                if (!current.contains(deviceId)) {
                    queueChange(shopId, group, CompatibilityChangeRequest.Action.ADD_DEVICE, deviceId, null);
                }
            }
            return toResponse(shopId, group);
        }

        groupDeviceRepository.deleteByCompatibilityGroupId(groupId);
        groupDeviceRepository.flush();
        for (int i = 0; i < desired.size(); i++) {
            addDeviceInternal(shopId, group, desired.get(i), i == 0, null);
        }
        return toResponse(shopId, group);
    }

    @Transactional
    public CompatibilityGroupResponse addDevice(UUID shopId, UUID groupId, GroupDeviceRequest request) {
        CompatibilityGroup group = require(shopId, groupId);
        if (requiresApproval(shopId)) {
            queueChange(shopId, group, CompatibilityChangeRequest.Action.ADD_DEVICE, request.deviceModelId(),
                    request.note());
            return toResponse(shopId, group);
        }
        addDeviceInternal(shopId, group, request.deviceModelId(),
                Boolean.TRUE.equals(request.primaryDevice()), request.note());
        return toResponse(shopId, group);
    }

    @Transactional
    public CompatibilityGroupResponse removeDevice(UUID shopId, UUID groupId, UUID deviceModelId) {
        CompatibilityGroup group = require(shopId, groupId);
        if (requiresApproval(shopId)) {
            queueChange(shopId, group, CompatibilityChangeRequest.Action.REMOVE_DEVICE, deviceModelId, null);
            return toResponse(shopId, group);
        }
        applyRemove(shopId, group, deviceModelId);
        return toResponse(shopId, group);
    }

    @Transactional(readOnly = true)
    public List<CompatibilityHistory> history(UUID shopId, UUID groupId) {
        require(shopId, groupId);
        return historyRepository.findByShopIdAndGroupIdOrderByCreatedAtDesc(shopId, groupId);
    }

    @Transactional(readOnly = true)
    public List<CompatibilityChangeRequest> pendingRequests(UUID shopId) {
        return changeRequestRepository.findByShopIdAndStatusOrderByCreatedAtDesc(
                shopId, CompatibilityChangeRequest.Status.PENDING);
    }

    @Transactional
    public CompatibilityGroupResponse decide(UUID shopId, UUID requestId, boolean approve) {
        CompatibilityChangeRequest request = changeRequestRepository.findByIdAndShopId(requestId, shopId)
                .orElseThrow(() -> ApiException.notFound("Change request", requestId));
        CompatibilityGroup group = require(shopId, request.getGroupId());
        request.setReviewedBy(CurrentUser.find().map(com.fixflow.security.UserPrincipal::getId).orElse(null));
        request.setReviewedAt(java.time.Instant.now());
        if (!approve) {
            request.setStatus(CompatibilityChangeRequest.Status.REJECTED);
            changeRequestRepository.save(request);
            return toResponse(shopId, group);
        }
        request.setStatus(CompatibilityChangeRequest.Status.APPROVED);
        changeRequestRepository.save(request);
        if (request.getAction() == CompatibilityChangeRequest.Action.ADD_DEVICE) {
            addDeviceInternal(shopId, group, request.getDeviceId(), false, request.getReason());
        } else {
            applyRemove(shopId, group, request.getDeviceId());
        }
        auditService.record(AuditAction.COMPATIBILITY_APPROVED, "CompatibilityGroup", group.getId(),
                "Approved a compatibility change on \"%s\"".formatted(group.getName()));
        return toResponse(shopId, group);
    }

    // -----------------------------------------------------------------

    private List<UUID> resolveMembershipIds(UUID shopId, List<UUID> deviceModelIds, List<String> deviceTexts) {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        if (deviceTexts != null && !deviceTexts.isEmpty()) {
            for (String text : deviceTexts) {
                if (text == null || text.isBlank()) {
                    continue;
                }
                ordered.add(deviceService.findOrCreateFromText(shopId, text.trim(), null).getId());
            }
            return new ArrayList<>(ordered);
        }
        if (deviceModelIds != null) {
            ordered.addAll(deviceModelIds);
        }
        return new ArrayList<>(ordered);
    }

    private void addDeviceInternal(UUID shopId, CompatibilityGroup group, UUID deviceModelId,
                                   boolean primary, String note) {
        DeviceModel device = deviceModelRepository.findByIdAndShopId(deviceModelId, shopId)
                .orElseThrow(() -> ApiException.notFound("Device model", deviceModelId));

        if (groupDeviceRepository.existsByCompatibilityGroupIdAndDeviceModelId(
                group.getId(), deviceModelId)) {
            return;
        }

        CompatibilityGroupDevice link = new CompatibilityGroupDevice();
        link.setCompatibilityGroupId(group.getId());
        link.setDeviceModelId(deviceModelId);
        link.setPrimaryDevice(primary);
        link.setNote(note);
        groupDeviceRepository.save(link);

        auditService.record(AuditAction.COMPATIBILITY_LINK_ADDED, "CompatibilityGroup", group.getId(),
                "Added %s to group \"%s\"".formatted(device.getName(), group.getName()));
        recordHistory(shopId, group, "Added " + device.getName(), Map.of("deviceId", deviceModelId.toString()));
    }

    private void applyRemove(UUID shopId, CompatibilityGroup group, UUID deviceModelId) {
        CompatibilityGroupDevice link = groupDeviceRepository
                .findByCompatibilityGroupIdAndDeviceModelId(group.getId(), deviceModelId)
                .orElseThrow(() -> ApiException.notFound("Device in group", deviceModelId));
        groupDeviceRepository.delete(link);
        auditService.record(AuditAction.COMPATIBILITY_LINK_REMOVED, "CompatibilityGroup", group.getId(),
                "Removed a device from group \"%s\"".formatted(group.getName()));
        recordHistory(shopId, group, "Removed device " + deviceModelId, Map.of("deviceId", deviceModelId.toString()));
    }

    private boolean requiresApproval(UUID shopId) {
        if (CurrentUser.has(Permission.COMPATIBILITY_APPROVE)) {
            return false;
        }
        boolean shopFlag = shopRepository.findById(shopId)
                .map(com.fixflow.shop.domain.Shop::isRequireCompatibilityApproval)
                .orElse(false);
        return shopFlag || featureFlagService.enabled(shopId, "COMPATIBILITY_APPROVAL");
    }

    private void queueChange(UUID shopId, CompatibilityGroup group, CompatibilityChangeRequest.Action action,
                             UUID deviceId, String reason) {
        CompatibilityChangeRequest request = new CompatibilityChangeRequest();
        request.setShopId(shopId);
        request.setGroupId(group.getId());
        request.setAction(action);
        request.setDeviceId(deviceId);
        request.setReason(reason);
        request.setRequestedBy(CurrentUser.find().map(com.fixflow.security.UserPrincipal::getId).orElse(null));
        changeRequestRepository.save(request);
    }

    private void recordHistory(UUID shopId, CompatibilityGroup group, String summary, Map<String, Object> after) {
        CompatibilityHistory row = new CompatibilityHistory();
        row.setShopId(shopId);
        row.setGroupId(group.getId());
        CurrentUser.find().ifPresent(principal -> {
            row.setActorId(principal.getId());
            row.setActorName(principal.getFullName());
        });
        row.setSummary(summary);
        row.setAfterData(after);
        historyRepository.save(row);
        auditService.record(AuditAction.COMPATIBILITY_CHANGED, "CompatibilityGroup", group.getId(), summary);
    }

    private String resolveCode(UUID shopId, CompatibilityGroupRequest request) {
        String base = request.code() != null && !request.code().isBlank()
                ? TextNormalizer.toCode(request.code())
                : TextNormalizer.toCode(request.name()) + "_GROUP";

        if (!groupRepository.existsByShopIdAndCode(shopId, base)) {
            return base;
        }
        // REALME_DISPLAY_GROUP -> REALME_DISPLAY_GROUP_002, _003, ...
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = "%s_%03d".formatted(base, suffix);
            if (!groupRepository.existsByShopIdAndCode(shopId, candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Could not generate a unique code for this group.");
    }

    private void apply(UUID shopId, CompatibilityGroup group, CompatibilityGroupRequest request) {
        group.setName(request.name().trim());
        group.setNotes(request.notes());
        if (request.categoryId() != null) {
            categoryRepository.findByIdAndShopId(request.categoryId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Category", request.categoryId()));
            group.setCategoryId(request.categoryId());
        }
        if (request.verified() != null) {
            group.setVerified(request.verified());
        }
        if (request.active() != null) {
            group.setActive(request.active());
        }
    }

    private CompatibilityGroupResponse toResponse(UUID shopId, CompatibilityGroup group) {
        List<CompatibilityGroupDevice> links = groupDeviceRepository
                .findByCompatibilityGroupId(group.getId());

        Map<UUID, CompatibilityGroupDevice> linkByDevice = links.stream()
                .collect(Collectors.toMap(CompatibilityGroupDevice::getDeviceModelId, link -> link, (a, b) -> a));

        List<UUID> deviceIds = links.stream().map(CompatibilityGroupDevice::getDeviceModelId).toList();
        List<DeviceModel> devices = deviceIds.isEmpty()
                ? List.of()
                : deviceModelRepository.findByShopIdAndIdIn(shopId, deviceIds);

        Map<UUID, Integer> order = new HashMap<>();
        for (int i = 0; i < links.size(); i++) {
            order.put(links.get(i).getDeviceModelId(), i);
        }

        List<GroupDeviceResponse> deviceResponses = devices.stream()
                .map(device -> new GroupDeviceResponse(
                        device.getId(),
                        device.getName(),
                        device.getBrand().getName(),
                        device.getVariant(),
                        linkByDevice.getOrDefault(device.getId(), new CompatibilityGroupDevice()).isPrimaryDevice()))
                .sorted(Comparator
                        .comparing((GroupDeviceResponse row) -> order.getOrDefault(row.deviceModelId(), Integer.MAX_VALUE))
                        .thenComparing(GroupDeviceResponse::deviceName))
                .toList();

        String categoryName = group.getCategoryId() == null ? null
                : categoryRepository.findByIdAndShopId(group.getCategoryId(), shopId)
                .map(Category::getName)
                .orElse(null);

        return new CompatibilityGroupResponse(group.getId(), group.getCode(), group.getName(),
                group.getCategoryId(), categoryName, group.getNotes(), group.isVerified(),
                group.isActive(), deviceResponses,
                productCompatibilityRepository.countByCompatibilityGroupId(group.getId()),
                group.getCreatedAt());
    }

    private CompatibilityGroup require(UUID shopId, UUID id) {
        return groupRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Compatibility group", id));
    }

    private static String trimName(String name) {
        if (name.length() <= 160) {
            return name;
        }
        return name.substring(0, 160);
    }
}
