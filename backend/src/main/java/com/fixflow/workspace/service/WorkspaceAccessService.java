package com.fixflow.workspace.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.billing.service.BillingService;
import com.fixflow.catalog.repository.ProductRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.notify.NotificationService;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.Permission;
import com.fixflow.security.SystemRole;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.shop.service.ShopProvisioningService;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.MyWorkspacesResponse;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceMember;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The only way a caller is bound to a workspace.
 *
 * <p>Business services keep using {@code CurrentUser.shopId()} — that id is
 * put on the JWT only after this service has checked an ACTIVE membership.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final WorkspaceMembershipRepository membershipRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final ShopProvisioningService shopProvisioningService;
    private final AuditService auditService;
    private final BillingService billingService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public WorkspaceMembership requireActive(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = membershipRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_A_MEMBER,
                        "You are not a member of this workspace."));
        if (!membership.isActive()) {
            throw new ApiException(ErrorCode.MEMBERSHIP_INACTIVE,
                    "Your membership is " + membership.getStatus() + ".");
        }
        Shop workspace = shopRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace", workspaceId));
        if (!workspace.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This workspace has been deactivated.");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public MyWorkspacesResponse listMine(UUID userId, UUID selectedWorkspaceId) {
        List<WorkspaceMembership> memberships = membershipRepository.findByUserIdOrderByLastSelectedAtDesc(userId);
        List<WorkspaceCard> cards = memberships.stream()
                .filter(m -> m.getStatus() != MembershipStatus.REMOVED)
                .sorted(Comparator
                        .comparing((WorkspaceMembership m) -> m.getStatus() != MembershipStatus.ACTIVE)
                        .thenComparing(m -> m.getLastSelectedAt() == null ? Instant.EPOCH : m.getLastSelectedAt(),
                                Comparator.reverseOrder()))
                .map(membership -> toCard(membership, selectedWorkspaceId))
                .toList();
        return new MyWorkspacesResponse(selectedWorkspaceId, cards);
    }

    @Transactional
    public WorkspaceCard create(UUID userId, CreateWorkspaceRequest request) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));

        String workspaceName = request.name().trim();
        Shop workspace = new Shop();
        workspace.setName(request.shopName() == null || request.shopName().isBlank()
                ? workspaceName : request.shopName().trim());
        workspace.setPhone(request.phone() == null ? user.getPhone() : request.phone());
        workspace.setEmail(request.email() == null ? user.getEmail() : request.email());
        workspace.setAddressLine1(request.addressLine1());
        workspace.setCity(request.city());
        workspace.setJoinCode(uniqueJoinCode(workspaceName));
        shopRepository.save(workspace);
        shopProvisioningService.seedCategories(workspace);
        billingService.notifyPaymentPending(workspace.getId(), user.getId(), user.getEmail());

        Role ownerRole = systemRole(SystemRole.OWNER);
        WorkspaceMembership membership = activate(user, workspace, ownerRole, userId);

        user.setShopId(workspace.getId());
        if (!user.getRoles().contains(ownerRole)) {
            user.getRoles().add(ownerRole);
        }
        userRepository.save(user);

        auditService.recordForShop(workspace.getId(), user.getFullName(), AuditAction.WORKSPACE_CREATED,
                "Workspace", workspace.getId(),
                "%s created workspace \"%s\"".formatted(user.getFullName(), workspace.getName()));

        return toCard(membership, workspace.getId());
    }

    @Transactional
    public com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse beginPaidJoin(UUID userId, String rawCode) {
        Shop workspace = requireJoinTarget(rawCode);
        rejectIfAlreadyWaitingOrActive(workspace.getId(), userId);
        if (isAdmin(userId) || billingService.hasUnspentJoinPayment(userId, workspace.getId())) {
            return com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse.alreadyPaid(workspace.getName());
        }
        var order = billingService.createJoinOrder(userId, workspace.getId());
        return new com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse(
                order.id(), order.orderId(), order.amount(), order.currency(), order.keyId(),
                order.priceCode(), order.gateway(), workspace.getName(), false);
    }

    @Transactional
    public WorkspaceCard completePaidJoin(UUID userId,
                                          com.fixflow.workspace.dto.WorkspaceDtos.CompleteJoinRequest request) {
        Shop workspace = requireJoinTarget(request == null ? null : request.joinCode());
        rejectIfAlreadyWaitingOrActive(workspace.getId(), userId);
        if (!isAdmin(userId) && !billingService.hasUnspentJoinPayment(userId, workspace.getId())) {
            if (request != null && request.razorpayOrderId() != null && !request.razorpayOrderId().isBlank()) {
                billingService.verifyJoinPayment(userId, new com.fixflow.billing.service.BillingService.VerifyPaymentRequest(
                        request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature()));
            } else if (request != null && request.orderId() != null) {
                billingService.confirmJoinPayment(userId, request.orderId());
            } else {
                throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                        "Pay ₹50 to request access. Each shop join is a separate payment.");
            }
        }
        return requestJoin(userId, workspace.getJoinCode());
    }

    /**
     * The joiner withdraws a request that has not been approved yet.
     * Deletes the pending row so People no longer shows it, and puts the
     * ₹50 back on this shop so they can ask again without paying twice.
     */
    @Transactional
    public void cancelJoinRequest(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = membershipRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Join request", workspaceId));
        if (membership.getStatus() != MembershipStatus.PENDING
                && membership.getStatus() != MembershipStatus.INVITED) {
            throw ApiException.businessRule("Only a waiting request can be cancelled.");
        }
        Shop workspace = shopRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace", workspaceId));
        User user = userRepository.findById(userId).orElseThrow();
        UUID membershipId = membership.getId();
        membershipRepository.delete(membership);
        billingService.releaseJoinPayment(userId, workspaceId);
        auditService.recordForShop(workspaceId, user.getFullName(), AuditAction.MEMBERSHIP_CANCELLED,
                "WorkspaceMembership", membershipId,
                "%s cancelled their request to join \"%s\"".formatted(user.getFullName(), workspace.getName()));
        notifyApprovers(workspace, "JOIN_REQUEST_CANCELLED",
                user.getFullName() + " cancelled a join request",
                "%s withdrew the request to join %s.".formatted(user.getFullName(), workspace.getName()));
    }

    /**
     * Join-by-code. Never grants access: the owner still has to approve.
     * Requires a captured, unused WORKSPACE_JOIN payment for this shop.
     */
    @Transactional
    public WorkspaceCard requestJoin(UUID userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        Shop workspace = shopRepository.findByJoinCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.notFound("Workspace", code));
        rejectIfAlreadyWaitingOrActive(workspace.getId(), userId);
        if (!billingService.consumeJoinPayment(userId, workspace.getId())) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "Pay ₹50 to request access. Each shop join is a separate payment.");
        }

        var existing = membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId);
        if (existing.isPresent()) {
            WorkspaceMembership membership = existing.get();
            if (membership.getStatus() == MembershipStatus.REMOVED
                    || membership.getStatus() == MembershipStatus.REJECTED) {
                membership.setStatus(MembershipStatus.PENDING);
                membership.setJoinedAt(null);
                membershipRepository.save(membership);
                recordJoinRequest(userId, workspace, membership);
                return toCard(membership, null);
            }
            throw ApiException.alreadyExists("You already have a membership in this workspace.");
        }

        Role viewer = systemRole(SystemRole.VIEWER);
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspace.getId());
        membership.setUserId(userId);
        membership.setRole(viewer);
        membership.setStatus(MembershipStatus.PENDING);
        membershipRepository.save(membership);
        recordJoinRequest(userId, workspace, membership);
        return toCard(membership, null);
    }

    @Transactional
    public UserPrincipal select(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = requireActive(userId, workspaceId);
        membership.setLastSelectedAt(Instant.now());
        membershipRepository.save(membership);

        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        user.setShopId(workspaceId);
        userRepository.save(user);

        auditService.recordForShop(workspaceId, user.getFullName(), AuditAction.WORKSPACE_SELECTED,
                "Workspace", workspaceId, "%s opened this workspace".formatted(user.getFullName()));

        return UserPrincipal.forWorkspace(user, workspaceId, membership.getRole());
    }

    @Transactional(readOnly = true)
    public UserPrincipal principalFor(User user, UUID preferredWorkspaceId) {
        List<WorkspaceMembership> active = membershipRepository.findActiveForUser(
                user.getId(), MembershipStatus.ACTIVE);
        if (active.isEmpty()) {
            return UserPrincipal.unscoped(user);
        }

        WorkspaceMembership chosen = active.stream()
                .filter(m -> m.getWorkspaceId().equals(preferredWorkspaceId))
                .findFirst()
                .or(() -> active.stream()
                        .filter(m -> m.getWorkspaceId().equals(user.getShopId()))
                        .findFirst())
                .orElse(active.get(0));

        return UserPrincipal.forWorkspace(user, chosen.getWorkspaceId(), chosen.getRole());
    }

    @Transactional(readOnly = true)
    public Page<WorkspaceMember> listMembers(UUID workspaceId, Pageable pageable) {
        requireActive(CurrentUser.userId(), workspaceId);
        return membershipRepository.findByWorkspaceId(workspaceId, pageable).map(this::toMember);
    }

    @Transactional
    public WorkspaceMember approve(UUID workspaceId, UUID membershipId, String roleCode) {
        requireCanApproveMembers(workspaceId);
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("Membership", membershipId));
        if (!membership.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Membership", membershipId);
        }
        if (roleCode != null && !roleCode.isBlank()) {
            membership.setRole(systemRole(SystemRole.valueOf(roleCode.trim().toUpperCase())));
        }
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(Instant.now());
        membershipRepository.save(membership);
        User user = userRepository.findById(membership.getUserId()).orElseThrow();
        auditService.recordForShop(workspaceId, CurrentUser.require().getFullName(), AuditAction.MEMBER_APPROVED,
                "WorkspaceMembership", membership.getId(),
                "Approved %s as %s".formatted(user.getFullName(), membership.getRole().getCode()));
        Shop workspace = shopRepository.findById(workspaceId).orElseThrow();
        notificationService.emit(workspaceId, user.getId(), "JOIN_REQUEST_APPROVED", user.getEmail(),
                "You joined " + workspace.getName(),
                "Your request to join %s was approved as %s.".formatted(
                        workspace.getName(), membership.getRole().getCode()));
        return toMember(membership);
    }

    @Transactional
    public WorkspaceMember reject(UUID workspaceId, UUID membershipId) {
        return setStatus(workspaceId, membershipId, MembershipStatus.REJECTED, AuditAction.MEMBER_REJECTED, false);
    }

    @Transactional
    public WorkspaceMember suspend(UUID workspaceId, UUID membershipId) {
        return setStatus(workspaceId, membershipId, MembershipStatus.SUSPENDED, AuditAction.MEMBER_SUSPENDED, true);
    }

    @Transactional
    public WorkspaceMember remove(UUID workspaceId, UUID membershipId) {
        return setStatus(workspaceId, membershipId, MembershipStatus.REMOVED, AuditAction.MEMBER_REMOVED, true);
    }

    /**
     * Drops a leftover membership from this shop. If they have no other shops,
     * their login is deleted so the email can register again.
     */
    @Transactional
    public void purge(UUID workspaceId, UUID membershipId) {
        requireOwnerOrAdmin(workspaceId);
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("Membership", membershipId));
        if (!membership.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Membership", membershipId);
        }
        if (membership.getUserId().equals(CurrentUser.userId())) {
            throw ApiException.businessRule("You cannot permanently delete your own membership.");
        }
        if (SystemRole.OWNER.name().equals(membership.getRole().getCode())) {
            throw ApiException.forbidden("The owner membership cannot be deleted.");
        }
        if (membership.getStatus() != MembershipStatus.REMOVED
                && membership.getStatus() != MembershipStatus.REJECTED) {
            throw ApiException.businessRule("Remove or reject them first, then delete permanently.");
        }
        User user = userRepository.findById(membership.getUserId()).orElseThrow();
        membershipRepository.delete(membership);
        if (!user.isSystemAdmin() && !membershipRepository.existsByUserId(user.getId())) {
            user.getRoles().clear();
            userRepository.delete(user);
            auditService.recordForShop(workspaceId, CurrentUser.require().getFullName(), AuditAction.MEMBER_PURGED,
                    "User", user.getId(),
                    "Permanently deleted %s from this shop and removed their login".formatted(user.getFullName()));
            return;
        }
        if (workspaceId.equals(user.getShopId())) {
            user.setShopId(null);
            userRepository.save(user);
        }
        auditService.recordForShop(workspaceId, CurrentUser.require().getFullName(), AuditAction.MEMBER_PURGED,
                "WorkspaceMembership", membershipId,
                "Permanently removed %s from this shop's people list".formatted(user.getFullName()));
    }

    private WorkspaceMember setStatus(UUID workspaceId, UUID membershipId, MembershipStatus status,
                                      AuditAction action, boolean ownerAdminOnly) {
        if (ownerAdminOnly) {
            requireOwnerOrAdmin(workspaceId);
        } else {
            requireCanApproveMembers(workspaceId);
        }
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("Membership", membershipId));
        if (!membership.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Membership", membershipId);
        }
        membership.setStatus(status);
        membershipRepository.save(membership);
        User user = userRepository.findById(membership.getUserId()).orElseThrow();
        auditService.recordForShop(workspaceId, CurrentUser.require().getFullName(), action,
                "WorkspaceMembership", membership.getId(),
                "%s is now %s".formatted(user.getFullName(), status));
        return toMember(membership);
    }

    private void requireOwnerOrAdmin(UUID workspaceId) {
        WorkspaceMembership actor = requireActive(CurrentUser.userId(), workspaceId);
        if (!Set.of(SystemRole.OWNER.name(), SystemRole.ADMIN.name()).contains(actor.getRole().getCode())) {
            throw ApiException.forbidden("Only an owner or admin can manage memberships.");
        }
    }

    private void requireCanApproveMembers(UUID workspaceId) {
        WorkspaceMembership actor = requireActive(CurrentUser.userId(), workspaceId);
        if (canApproveMembers(actor)) {
            return;
        }
        throw ApiException.forbidden(
                "You need People edit permission, or an Owner/Admin role, to approve join requests.");
    }

    private static boolean canApproveMembers(WorkspaceMembership actor) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }
        String code = actor.getRole().getCode();
        if (SystemRole.OWNER.name().equals(code) || SystemRole.ADMIN.name().equals(code)) {
            return true;
        }
        return actor.getRole().permissionCodes().contains(Permission.USER_WRITE);
    }

    /** Used by shop provisioning and the demo seeder so every user gets a membership. */
    @Transactional
    public WorkspaceMembership activate(User user, Shop workspace, Role role, UUID invitedBy) {
        WorkspaceMembership membership = membershipRepository
                .findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseGet(WorkspaceMembership::new);
        membership.setWorkspaceId(workspace.getId());
        membership.setUserId(user.getId());
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setInvitedBy(invitedBy);
        membership.setJoinedAt(Instant.now());
        membership.setLastSelectedAt(Instant.now());
        return membershipRepository.save(membership);
    }

    private WorkspaceCard toCard(WorkspaceMembership membership, UUID selectedWorkspaceId) {
        Shop workspace = shopRepository.findById(membership.getWorkspaceId()).orElseThrow();
        boolean privileged = Set.of(SystemRole.OWNER.name(), SystemRole.ADMIN.name())
                .contains(membership.getRole().getCode());
        return new WorkspaceCard(
                workspace.getId(),
                workspace.getName(),
                workspace.getCity(),
                workspace.getLogoUrl(),
                membership.isActive() && privileged ? workspace.getJoinCode() : null,
                membership.getRole().getCode(),
                membership.getStatus(),
                membershipRepository.countByWorkspaceIdAndStatus(workspace.getId(), MembershipStatus.ACTIVE),
                productRepository.countByShopIdAndActiveTrue(workspace.getId()),
                workspace.getId().equals(selectedWorkspaceId));
    }

    private WorkspaceMember toMember(WorkspaceMembership membership) {
        User user = userRepository.findById(membership.getUserId()).orElseThrow();
        return new WorkspaceMember(membership.getId(), user.getId(), user.getFullName(), user.getEmail(),
                membership.getRole().getCode(), membership.getStatus(), membership.getJoinedAt());
    }

    private void recordJoinRequest(UUID userId, Shop workspace, WorkspaceMembership membership) {
        User user = userRepository.findById(userId).orElseThrow();
        auditService.recordForShop(workspace.getId(), user.getFullName(), AuditAction.MEMBERSHIP_REQUESTED,
                "WorkspaceMembership", membership.getId(),
                "%s asked to join \"%s\"".formatted(user.getFullName(), workspace.getName()));
        notifyApprovers(workspace, "JOIN_REQUEST",
                user.getFullName() + " asked to join " + workspace.getName(),
                "%s (%s) paid and requested access. Switch to this shop, then open People to approve or reject."
                        .formatted(user.getFullName(), user.getEmail()));
    }

    private void notifyApprovers(Shop workspace, String eventType, String subject, String body) {
        membershipRepository.findAllByWorkspaceIdAndStatus(workspace.getId(), MembershipStatus.ACTIVE).stream()
                .filter(WorkspaceAccessService::canApproveMembers)
                .forEach(approver -> userRepository.findById(approver.getUserId()).ifPresent(target ->
                        notificationService.emit(workspace.getId(), target.getId(), eventType,
                                target.getEmail(), subject, body)));
    }

    private String uniqueJoinCode(String name) {
        for (int attempt = 0; attempt < 12; attempt++) {
            String code = JoinCodeGenerator.generate(name);
            if (!shopRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw ApiException.conflict("Could not allocate a join code. Try a different workspace name.");
    }

    private Shop requireJoinTarget(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        return shopRepository.findByJoinCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.notFound("Workspace", code));
    }

    private void rejectIfAlreadyWaitingOrActive(UUID workspaceId, UUID userId) {
        membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId).ifPresent(membership -> {
            if (membership.getStatus() == MembershipStatus.ACTIVE
                    || membership.getStatus() == MembershipStatus.PENDING) {
                throw ApiException.alreadyExists("You already have a membership in this workspace.");
            }
        });
    }

    private boolean isAdmin(UUID userId) {
        return userRepository.findById(userId).map(User::isSystemAdmin).orElse(false);
    }

    private Role systemRole(SystemRole systemRole) {
        return roleRepository.findSystemRoleByCode(systemRole.name())
                .orElseThrow(() -> new IllegalStateException("Missing system role " + systemRole));
    }
}
