package com.fixflow.user.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.common.error.ApiException;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.dto.UserDtos.CreateUserRequest;
import com.fixflow.user.dto.UserDtos.RoleResponse;
import com.fixflow.user.dto.UserDtos.UpdateUserRequest;
import com.fixflow.user.dto.UserDtos.UserResponse;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import com.fixflow.workspace.service.WorkspaceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ShopRepository shopRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuditService auditService;
    private final com.fixflow.billing.service.BillingService billingService;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(UUID shopId, Pageable pageable) {
        return membershipRepository
                .findByWorkspaceIdAndStatus(shopId, MembershipStatus.ACTIVE, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID shopId, UUID id) {
        return toResponse(requireMembership(shopId, id));
    }

    @Transactional
    public UserResponse create(UUID shopId, CreateUserRequest request) {
        billingService.requireMemberSeat(shopId);
        Set<Role> roles = resolveRoles(shopId, request.roles());
        assertAssignable(roles);
        Role primary = mostSenior(roles);
        Shop workspace = shopRepository.findById(shopId)
                .orElseThrow(() -> ApiException.notFound("Workspace", shopId));

        return userRepository.findWithRolesByEmail(request.email()).map(existing -> {
            membershipRepository.findByWorkspaceIdAndUserId(shopId, existing.getId()).ifPresent(membership -> {
                if (membership.getStatus() != MembershipStatus.REMOVED
                        && membership.getStatus() != MembershipStatus.REJECTED) {
                    throw ApiException.alreadyExists("This person is already in this workspace.");
                }
            });
            WorkspaceMembership membership = workspaceAccessService.activate(
                    existing, workspace, primary, CurrentUser.userId());
            if (!existing.getRoles().contains(primary)) {
                existing.getRoles().add(primary);
                userRepository.save(existing);
            }
            auditService.record(AuditAction.MEMBERSHIP_ACTIVATED, "User", existing.getId(),
                    "Added \"%s\" to this workspace as %s".formatted(existing.getFullName(), primary.getCode()));
            return toResponse(membership);
        }).orElseGet(() -> {
            // A placeholder row under a fresh id. When this person first signs in through Identity
            // the security filter finds no row under their Identity subject, falls back to this
            // email, and uses this row, so the membership granted here is theirs from day one.
            User user = new User();
            user.setShopId(shopId);
            user.setFullName(request.fullName().trim());
            user.setEmail(request.email().toLowerCase());
            user.setPhone(request.phone());
            user.setRoles(roles);
            userRepository.save(user);
            WorkspaceMembership membership = workspaceAccessService.activate(
                    user, workspace, primary, CurrentUser.userId());
            auditService.record(AuditAction.USER_CREATED, "User", user.getId(),
                    "Created user \"%s\" with roles %s".formatted(user.getFullName(), request.roles()));
            return toResponse(membership);
        });
    }

    @Transactional
    public UserResponse update(UUID shopId, UUID id, UpdateUserRequest request) {
        WorkspaceMembership membership = requireMembership(shopId, id);
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> ApiException.notFound("User", id));
        UserPrincipal actor = CurrentUser.require();

        if (user.getId().equals(actor.getId()) && Boolean.FALSE.equals(request.active())) {
            throw ApiException.businessRule("You cannot deactivate your own account.");
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        if (request.active() != null) {
            user.setActive(request.active());
        }

        if (request.roles() != null && !request.roles().isEmpty()) {
            Set<Role> roles = resolveRoles(shopId, request.roles());
            assertAssignable(roles);
            Role primary = mostSenior(roles);
            membership.setRole(primary);
            membershipRepository.save(membership);
            user.setRoles(roles);
            auditService.record(AuditAction.USER_ROLES_CHANGED, "User", id,
                    "Changed roles for \"%s\" to %s".formatted(user.getFullName(), request.roles()));
        }

        userRepository.save(user);

        if (Boolean.FALSE.equals(request.active())) {
            auditService.record(AuditAction.USER_DEACTIVATED, "User", id,
                    "Deactivated user \"%s\"".formatted(user.getFullName()));
        } else {
            auditService.record(AuditAction.USER_UPDATED, "User", id,
                    "Updated user \"%s\"".formatted(user.getFullName()));
        }
        return toResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(UUID shopId) {
        return roleRepository.findAssignableForShop(shopId).stream()
                .map(this::toRoleResponse)
                .toList();
    }

    /**
     * A manager cannot grant OWNER, and nobody can assign a role they themselves
     * do not hold (except the owner, who holds everything).
     */
    private void assertAssignable(Set<Role> requested) {
        UserPrincipal actor = CurrentUser.require();
        int actorSeniority = actor.getRoles().contains("OWNER") ? 0 : 100;
        if (!actor.getRoles().contains("OWNER")) {
            actorSeniority = roleRepository.findAssignableForShop(actor.getShopId()).stream()
                    .filter(role -> actor.getRoles().contains(role.getCode()))
                    .mapToInt(Role::getSeniority)
                    .min()
                    .orElse(100);
        }

        int threshold = actorSeniority;
        requested.forEach(role -> {
            if (role.getSeniority() < threshold) {
                throw ApiException.forbidden(
                        "You cannot assign the %s role.".formatted(role.getCode()));
            }
        });
    }

    private Set<Role> resolveRoles(UUID shopId, Set<String> codes) {
        List<Role> found = roleRepository.findAssignableByCodes(shopId, codes);
        if (found.size() != codes.size()) {
            Set<String> foundCodes = found.stream().map(Role::getCode).collect(Collectors.toSet());
            Set<String> missing = new LinkedHashSet<>(codes);
            missing.removeAll(foundCodes);
            throw ApiException.businessRule("Unknown roles: " + missing);
        }
        return new LinkedHashSet<>(found);
    }

    private WorkspaceMembership requireMembership(UUID shopId, UUID userId) {
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(shopId, userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        if (!membership.isActive()) {
            throw ApiException.notFound("User", userId);
        }
        return membership;
    }

    private UserResponse toResponse(WorkspaceMembership membership) {
        User user = userRepository.findWithRolesById(membership.getUserId()).orElseThrow();
        return toResponse(user, membership.getRole());
    }

    private UserResponse toResponse(User user, Role workspaceRole) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(workspaceRole.getCode());
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getAvatarUrl(), user.isActive(),
                user.getLastLoginAt(), roles,
                workspaceRole.permissionCodes().stream().map(Permission::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.isSystemRole(), role.getSeniority(),
                role.permissionCodes().stream().map(Permission::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static Role mostSenior(Set<Role> roles) {
        return roles.stream()
                .min(Comparator.comparingInt(Role::getSeniority))
                .orElseThrow(() -> ApiException.businessRule("At least one role is required."));
    }
}
