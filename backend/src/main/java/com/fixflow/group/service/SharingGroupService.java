package com.fixflow.group.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.group.domain.GroupRole;
import com.fixflow.group.domain.SharingGroup;
import com.fixflow.group.domain.SharingGroupMember;
import com.fixflow.group.repository.SharingGroupMemberRepository;
import com.fixflow.group.repository.SharingGroupRepository;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.service.JoinCodeGenerator;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may see a fitment group, and who may add or remove a shop or a person.
 *
 * <p>An admin can add shops and people, and can appoint another person as admin.
 * Only the owner can dismiss an admin. The owner cannot be removed.
 */
@Service
@RequiredArgsConstructor
public class SharingGroupService {

    public static final String GROUP_HEADER = "X-Fitment-Group";

    public record GroupCard(UUID id, String name, GroupRole callerRole) {
    }

    public record MemberCard(String kind, UUID subjectId, String label, GroupRole role) {
    }

    public record GroupDetail(UUID id, String name, GroupRole callerRole, List<MemberCard> members, String joinCode) {
    }

    private final SharingGroupRepository groups;
    private final SharingGroupMemberRepository members;
    private final ShopRepository shops;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<GroupCard> listVisible() {
        UserPrincipal caller = CurrentUser.require();
        return visible(caller).stream()
                .map(group -> new GroupCard(group.getId(), group.getName(), roleOf(caller, group)))
                .toList();
    }

    @Transactional
    public GroupDetail detail(UUID groupId) {
        UserPrincipal caller = CurrentUser.require();
        SharingGroup group = requireVisible(caller, groupId);
        GroupRole role = roleOf(caller, group);
        String code = null;
        if (role == GroupRole.OWNER || role == GroupRole.ADMIN) {
            code = ensureJoinCode(group);
        }
        return new GroupDetail(group.getId(), group.getName(), role, memberCards(group), code);
    }

    @Transactional
    public GroupCard create(String name) {
        UserPrincipal caller = CurrentUser.require();
        String trimmed = requiredName(name);
        SharingGroup group = new SharingGroup();
        group.setName(trimmed);
        group.setOwnerUserId(caller.getId());
        group.setJoinCode(freshJoinCode(trimmed));
        groups.save(group);
        return new GroupCard(group.getId(), group.getName(), GroupRole.OWNER);
    }

    /**
     * The catalog that already existed, plus every current shop. Used when a database is seeded
     * after the migration found no user to own the default group.
     */
    @Transactional
    public UUID ensureDefault(UUID ownerUserId) {
        SharingGroup group = groups.findById(SharingGroup.DEFAULT_ID)
                .or(() -> groups.findFirstByName(SharingGroup.DEFAULT_NAME))
                .orElseGet(() -> {
                    SharingGroup created = new SharingGroup();
                    created.setName(SharingGroup.DEFAULT_NAME);
                    created.setOwnerUserId(ownerUserId);
                    return groups.save(created);
                });
        for (Shop shop : shops.findAll()) {
            if (members.findByGroupIdAndWorkspaceId(group.getId(), shop.getId()).isEmpty()) {
                SharingGroupMember row = new SharingGroupMember();
                row.setGroupId(group.getId());
                row.setWorkspaceId(shop.getId());
                row.setRole(GroupRole.MEMBER);
                members.save(row);
            }
        }
        return group.getId();
    }

    @Transactional
    public MemberCard addShop(UUID groupId, UUID workspaceId, String joinCode) {
        SharingGroup group = requireManaged(groupId);
        Shop shop = resolveShop(workspaceId, joinCode);
        if (members.findByGroupIdAndWorkspaceId(group.getId(), shop.getId()).isPresent()) {
            throw ApiException.alreadyExists(shop.getName() + " is already in this group.");
        }
        SharingGroupMember row = new SharingGroupMember();
        row.setGroupId(group.getId());
        row.setWorkspaceId(shop.getId());
        row.setRole(GroupRole.MEMBER);
        members.save(row);
        return new MemberCard("SHOP", shop.getId(), shop.getName(), GroupRole.MEMBER);
    }

    @Transactional
    public void removeShop(UUID groupId, UUID workspaceId) {
        SharingGroup group = requireManaged(groupId);
        SharingGroupMember row = members.findByGroupIdAndWorkspaceId(group.getId(), workspaceId)
                .orElseThrow(() -> ApiException.notFound("Shop membership", workspaceId));
        members.delete(row);
    }

    @Transactional
    public MemberCard addPerson(UUID groupId, String email, GroupRole requested) {
        UserPrincipal caller = CurrentUser.require();
        SharingGroup group = requireManaged(groupId);
        User person = users.findWithRolesByEmail(required(email, "An email is required."))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No account with that email."));
        if (person.getId().equals(group.getOwnerUserId())) {
            throw ApiException.businessRule("The owner is already in this group.");
        }
        GroupRole role = requested == GroupRole.ADMIN ? GroupRole.ADMIN : GroupRole.MEMBER;
        if (role == GroupRole.ADMIN) {
            requireCanAppoint(caller, group);
        }
        Optional<SharingGroupMember> existing = members.findByGroupIdAndUserId(group.getId(), person.getId());
        if (existing.isPresent()) {
            return setPersonRole(groupId, person.getId(), role);
        }
        SharingGroupMember row = new SharingGroupMember();
        row.setGroupId(group.getId());
        row.setUserId(person.getId());
        row.setRole(role);
        members.save(row);
        return new MemberCard("PERSON", person.getId(), label(person), role);
    }

    @Transactional
    public MemberCard setPersonRole(UUID groupId, UUID userId, GroupRole requested) {
        UserPrincipal caller = CurrentUser.require();
        SharingGroup group = requireManaged(groupId);
        if (userId.equals(group.getOwnerUserId())) {
            throw ApiException.businessRule("The owner's right cannot be changed.");
        }
        SharingGroupMember row = members.findByGroupIdAndUserId(group.getId(), userId)
                .orElseThrow(() -> ApiException.notFound("Person", userId));
        GroupRole next = requested == GroupRole.ADMIN ? GroupRole.ADMIN : GroupRole.MEMBER;
        if (row.getRole() == GroupRole.ADMIN && next == GroupRole.MEMBER) {
            requireOwner(caller, group);
        }
        if (next == GroupRole.ADMIN) {
            requireCanAppoint(caller, group);
        }
        row.setRole(next);
        members.save(row);
        User person = users.findById(userId).orElseThrow(() -> ApiException.notFound("Person", userId));
        return new MemberCard("PERSON", person.getId(), label(person), next);
    }

    @Transactional
    public void removePerson(UUID groupId, UUID userId) {
        UserPrincipal caller = CurrentUser.require();
        SharingGroup group = requireManaged(groupId);
        if (userId.equals(group.getOwnerUserId())) {
            throw ApiException.businessRule("The owner cannot be removed.");
        }
        SharingGroupMember row = members.findByGroupIdAndUserId(group.getId(), userId)
                .orElseThrow(() -> ApiException.notFound("Person", userId));
        if (row.getRole() == GroupRole.ADMIN) {
            requireOwner(caller, group);
        }
        members.delete(row);
    }

    /**
     * The group whose fitment this request reads or writes. The header wins when the caller
     * can see that group. One visible group needs no header. Several fall back to the
     * original catalog when the caller is still in it.
     */
    @Transactional(readOnly = true)
    public UUID resolveSelected() {
        UserPrincipal caller = CurrentUser.require();
        List<SharingGroup> visible = visible(caller);
        if (visible.isEmpty()) {
            return null;
        }
        Optional<UUID> requested = headerGroup();
        if (requested.isPresent()) {
            boolean allowed = visible.stream().anyMatch(group -> group.getId().equals(requested.get()));
            if (!allowed) {
                throw ApiException.forbidden("You are not in that fitment group.");
            }
            return requested.get();
        }
        if (visible.size() == 1) {
            return visible.get(0).getId();
        }
        return visible.stream()
                .filter(group -> SharingGroup.DEFAULT_ID.equals(group.getId()))
                .map(SharingGroup::getId)
                .findFirst()
                .orElseGet(() -> visible.get(0).getId());
    }

    @Transactional(readOnly = true)
    public UUID requireSelected() {
        UUID groupId = resolveSelected();
        if (groupId == null) {
            throw ApiException.forbidden("You are not in a fitment group.");
        }
        return groupId;
    }

    @Transactional(readOnly = true)
    public boolean canSee(UUID groupId) {
        if (groupId == null) {
            return false;
        }
        UserPrincipal caller = CurrentUser.require();
        return visible(caller).stream().anyMatch(group -> group.getId().equals(groupId));
    }

    @Transactional(readOnly = true)
    public long shopCount(UUID groupId) {
        if (groupId == null) {
            return 0;
        }
        return members.countByGroupIdAndWorkspaceIdIsNotNull(groupId);
    }

    @Transactional(readOnly = true)
    public String nameOf(UUID groupId) {
        if (groupId == null) {
            return null;
        }
        return groups.findById(groupId).map(SharingGroup::getName).orElse(null);
    }

    private List<SharingGroup> visible(UserPrincipal caller) {
        UUID shopId = caller.getShopId();
        return groups.visibleTo(caller.getId(), shopId == null ? new UUID(0L, 0L) : shopId, shopId != null);
    }

    public SharingGroup requireManager(UUID groupId) {
        return requireManaged(groupId);
    }

    private SharingGroup requireManaged(UUID groupId) {
        UserPrincipal caller = CurrentUser.require();
        SharingGroup group = requireVisible(caller, groupId);
        GroupRole role = roleOf(caller, group);
        if (role != GroupRole.OWNER && role != GroupRole.ADMIN) {
            throw ApiException.forbidden("Only an owner or admin can change who is in this group.");
        }
        return group;
    }

    private SharingGroup requireVisible(UserPrincipal caller, UUID groupId) {
        return visible(caller).stream()
                .filter(group -> group.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Fitment group", groupId));
    }

    private GroupRole roleOf(UserPrincipal caller, SharingGroup group) {
        if (caller.getId().equals(group.getOwnerUserId())) {
            return GroupRole.OWNER;
        }
        Optional<SharingGroupMember> direct = members.findByGroupIdAndUserId(group.getId(), caller.getId());
        if (direct.isPresent()) {
            return direct.get().getRole();
        }
        if (caller.getShopId() != null
                && members.findByGroupIdAndWorkspaceId(group.getId(), caller.getShopId()).isPresent()) {
            return GroupRole.MEMBER;
        }
        return GroupRole.MEMBER;
    }

    private List<MemberCard> memberCards(SharingGroup group) {
        List<MemberCard> cards = new ArrayList<>();
        users.findById(group.getOwnerUserId()).ifPresent(owner ->
                cards.add(new MemberCard("PERSON", owner.getId(), label(owner), GroupRole.OWNER)));
        for (SharingGroupMember row : members.findByGroupIdOrderByCreatedAtAsc(group.getId())) {
            if (row.getUserId() != null && row.getUserId().equals(group.getOwnerUserId())) {
                continue;
            }
            if (row.getWorkspaceId() != null) {
                String label = shops.findById(row.getWorkspaceId()).map(Shop::getName).orElse("Shop");
                cards.add(new MemberCard("SHOP", row.getWorkspaceId(), label, GroupRole.MEMBER));
            } else if (row.getUserId() != null) {
                User person = users.findById(row.getUserId()).orElse(null);
                String label = person == null ? "Person" : label(person);
                cards.add(new MemberCard("PERSON", row.getUserId(), label, row.getRole()));
            }
        }
        return cards;
    }

    private Shop resolveShop(UUID workspaceId, String joinCode) {
        if (workspaceId != null) {
            return shops.findById(workspaceId)
                    .orElseThrow(() -> ApiException.notFound("Shop", workspaceId));
        }
        String code = required(joinCode, "A shop id or a join code is required.");
        return shops.findByJoinCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No shop with that code."));
    }

    private static void requireOwner(UserPrincipal caller, SharingGroup group) {
        if (!caller.getId().equals(group.getOwnerUserId())) {
            throw ApiException.forbidden("Only the owner can change an admin.");
        }
    }

    private static void requireCanAppoint(UserPrincipal caller, SharingGroup group) {
        if (caller.getId().equals(group.getOwnerUserId())) {
            return;
        }
        // Caller already passed requireManaged, so a non-owner here is an admin.
    }

    private static String label(User person) {
        if (person.getFullName() != null && !person.getFullName().isBlank()) {
            return person.getFullName();
        }
        return person.getEmail();
    }

    @Transactional
    public void backfillJoinCodes() {
        for (SharingGroup group : groups.findAll()) {
            ensureJoinCode(group);
        }
    }

    private String ensureJoinCode(SharingGroup group) {
        if (group.getJoinCode() != null && !group.getJoinCode().isBlank()) {
            return group.getJoinCode();
        }
        String code = freshJoinCode(group.getName());
        group.setJoinCode(code);
        groups.save(group);
        return code;
    }

    private String freshJoinCode(String name) {
        for (int attempt = 0; attempt < 12; attempt++) {
            String code = JoinCodeGenerator.generate(name);
            if (groups.findByJoinCodeIgnoreCase(code).isEmpty()
                    && shops.findByJoinCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw ApiException.businessRule("Could not assign a join code.");
    }

    private static String requiredName(String name) {
        String trimmed = required(name, "A group needs a name.");
        if (trimmed.length() > 160) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A group name is at most 160 characters.");
        }
        return trimmed;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    private static Optional<UUID> headerGroup() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        String raw = servlet.getRequest().getHeader(GROUP_HEADER);
        if (raw == null || raw.isBlank()) {
            raw = servlet.getRequest().getParameter("groupId");
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, GROUP_HEADER + " is not a valid id.");
        }
    }
}
