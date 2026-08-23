package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Category;
import com.fixflow.catalog.domain.CompatibilityChangeRequest;
import com.fixflow.catalog.domain.CompatibilityGroup;
import com.fixflow.catalog.domain.CompatibilityGroupDevice;
import com.fixflow.catalog.domain.CompatibilityHistory;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityGroupResponse;
import com.fixflow.catalog.dto.CatalogDtos.GroupDeviceRequest;
import com.fixflow.catalog.dto.CatalogDtos.GroupDeviceResponse;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final CategoryRepository categoryRepository;
    private final ProductCompatibilityRepository productCompatibilityRepository;
    private final CompatibilityHistoryRepository historyRepository;
    private final CompatibilityChangeRequestRepository changeRequestRepository;
    private final ShopRepository shopRepository;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<CompatibilityGroupResponse> list(UUID shopId, UUID categoryId, Pageable pageable) {
        Page<CompatibilityGroup> page = categoryId == null
                ? groupRepository.findByShopId(shopId, pageable)
                : groupRepository.findByShopIdAndCategoryId(shopId, categoryId, pageable);
        return page.map(group -> toResponse(shopId, group));
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

        if (request.deviceModelIds() != null) {
            for (int i = 0; i < request.deviceModelIds().size(); i++) {
                UUID deviceId = request.deviceModelIds().get(i);
                // The first model listed becomes the one the group is named after.
                addDeviceInternal(shopId, group, deviceId, i == 0, null);
            }
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

        List<UUID> deviceIds = links.stream().map(CompatibilityGroupDevice::getDeviceModelId).toList();
        List<DeviceModel> devices = deviceIds.isEmpty()
                ? List.of()
                : deviceModelRepository.findByShopIdAndIdIn(shopId, deviceIds);

        List<GroupDeviceResponse> deviceResponses = devices.stream()
                .map(device -> new GroupDeviceResponse(
                        device.getId(),
                        device.getName(),
                        device.getBrand().getName(),
                        links.stream()
                                .filter(l -> l.getDeviceModelId().equals(device.getId()))
                                .findFirst()
                                .map(CompatibilityGroupDevice::isPrimaryDevice)
                                .orElse(false)))
                .sorted(Comparator
                        .comparing(GroupDeviceResponse::primaryDevice).reversed()
                        .thenComparing(GroupDeviceResponse::deviceName))
                .toList();

        String categoryName = group.getCategoryId() == null ? null
                : categoryRepository.findByIdAndShopId(group.getCategoryId(), shopId)
                .map(Category::getName)
                .orElse(null);

        return new CompatibilityGroupResponse(group.getId(), group.getCode(), group.getName(),
                group.getCategoryId(), categoryName, group.getNotes(), group.isVerified(),
                group.isActive(), deviceResponses,
                productCompatibilityRepository.countByCompatibilityGroupId(group.getId()));
    }

    private CompatibilityGroup require(UUID shopId, UUID id) {
        return groupRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Compatibility group", id));
    }
}
