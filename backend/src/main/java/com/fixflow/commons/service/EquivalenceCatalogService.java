package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceGroup;
import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceMember;
import com.fixflow.commons.repository.CatalogEquivalenceGroupRepository;
import com.fixflow.commons.repository.CatalogEquivalenceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A fitment group's own statement that these models take the same kind of part.
 * Devices must already belong to that group. Nothing here copies another group's list.
 */
@Service
@RequiredArgsConstructor
public class EquivalenceCatalogService {

    private final CatalogEquivalenceGroupRepository groups;
    private final CatalogEquivalenceMemberRepository members;
    private final CommonsCatalogService catalog;

    @Transactional(readOnly = true)
    public List<UUID> siblingDeviceIds(UUID groupId, UUID deviceId) {
        if (groupId == null || deviceId == null) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(deviceId);
        ids.addAll(members.findSiblingDeviceIds(groupId, deviceId));
        return List.copyOf(ids);
    }

    @Transactional(readOnly = true)
    public List<CatalogEquivalenceGroup> groupsForDevice(UUID groupId, UUID deviceId) {
        catalog.requireDevice(groupId, deviceId);
        return groups.findContainingDevice(groupId, deviceId);
    }

    @Transactional(readOnly = true)
    public List<CatalogDevice> membersOf(UUID groupId, UUID equivalenceGroupId) {
        CatalogEquivalenceGroup group = requireOwn(groupId, equivalenceGroupId);
        List<UUID> deviceIds = members.findByEquivalenceGroupId(group.getId()).stream()
                .map(CatalogEquivalenceMember::getDeviceId)
                .toList();
        List<CatalogDevice> devices = new ArrayList<>();
        for (UUID deviceId : deviceIds) {
            devices.add(catalog.requireDevice(groupId, deviceId));
        }
        return devices;
    }

    @Transactional
    public CatalogEquivalenceGroup create(UUID groupId,
                                          String categoryCode,
                                          String name,
                                          List<UUID> deviceIds,
                                          UUID actorId) {
        if (groupId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a fitment group.");
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A compatibility group needs a part type.");
        }
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A compatibility group needs a name.");
        }
        if (deviceIds == null || deviceIds.size() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Name at least two models that share this part.");
        }
        String code = categoryCode.trim().toUpperCase();
        String trimmed = name.trim();
        CatalogEquivalenceGroup group = groups.findByIdentity(groupId, code, trimmed).orElseGet(() -> {
            CatalogEquivalenceGroup created = new CatalogEquivalenceGroup();
            created.setGroupId(groupId);
            created.setCategoryCode(code);
            created.setName(trimmed);
            created.setCreatedBy(actorId);
            return groups.save(created);
        });
        boolean first = members.findByEquivalenceGroupId(group.getId()).isEmpty();
        int index = 0;
        for (UUID deviceId : deviceIds) {
            catalog.requireDevice(groupId, deviceId);
            members.findMembership(groupId, code, deviceId).ifPresent(existing -> {
                if (!existing.getEquivalenceGroupId().equals(group.getId())) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "That model is already in another " + code + " group in this fitment group.");
                }
            });
            boolean already = members.findByEquivalenceGroupId(group.getId()).stream()
                    .anyMatch(member -> member.getDeviceId().equals(deviceId));
            if (already) {
                index++;
                continue;
            }
            CatalogEquivalenceMember member = new CatalogEquivalenceMember();
            member.setEquivalenceGroupId(group.getId());
            member.setDeviceId(deviceId);
            member.setPrimaryDevice(first && index == 0);
            member.setCreatedBy(actorId);
            members.save(member);
            index++;
        }
        return group;
    }

    private CatalogEquivalenceGroup requireOwn(UUID groupId, UUID equivalenceGroupId) {
        CatalogEquivalenceGroup group = groups.findById(equivalenceGroupId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such compatibility group"));
        if (!group.getGroupId().equals(groupId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such compatibility group");
        }
        return group;
    }
}
