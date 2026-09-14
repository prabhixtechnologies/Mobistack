package com.fixflow.workspace.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.notify.NotificationService;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.SystemRole;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.Role;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.RoleRepository;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceInvitation;
import com.fixflow.workspace.dto.WorkspaceDtos.InvitationResponse;
import com.fixflow.workspace.dto.WorkspaceDtos.InviteMemberRequest;
import com.fixflow.workspace.repository.WorkspaceInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.fixflow.billing.service.BillingService billingService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InvitationResponse invite(UUID workspaceId, InviteMemberRequest request) {
        workspaceAccessService.requireSelected(workspaceId);
        billingService.require(workspaceId, "MEMBER_ADD");
        Role role = roleRepository.findSystemRoleByCode(request.role().trim().toUpperCase())
                .orElseThrow(() -> ApiException.businessRule("Unknown role " + request.role()));
        String token = generateToken();
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setWorkspaceId(workspaceId);
        invitation.setEmail(request.email());
        invitation.setPhone(request.phone());
        invitation.setRole(role);
        invitation.setTokenHash(hash(token));
        invitation.setRawHint(token.substring(0, 6));
        invitation.setStatus(WorkspaceInvitation.Status.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setInvitedBy(CurrentUser.userId());
        invitationRepository.save(invitation);

        Shop workspace = shopRepository.findById(workspaceId).orElseThrow();
        // Addressed to the invitee, not the inviter. Passing the caller's id here
        // put the invitation — token and all — in the inbox of the person who
        // sent it, and left the invitee with nothing.
        UUID invitee = userRepository.findWithRolesByEmail(request.email()).map(User::getId).orElse(null);
        notificationService.emit(workspaceId, invitee, "USER_INVITED", request.email(),
                "You have been invited to " + workspace.getName(),
                "Join %s as %s. Open the invitation email or the People screen — the join token is never stored in notifications."
                        .formatted(workspace.getName(), role.getCode()));
        auditService.record(AuditAction.MEMBER_INVITED, "WorkspaceInvitation", invitation.getId(),
                "Invited %s as %s".formatted(request.email(), role.getCode()));
        return toResponse(invitation, token);
    }

    @Transactional
    public void accept(String rawToken, UUID userId) {
        WorkspaceInvitation invitation = invitationRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invitation is not valid."));
        if (invitation.getStatus() != WorkspaceInvitation.Status.PENDING) {
            throw new ApiException(ErrorCode.INVITE_USED, "This invitation has already been used.");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(WorkspaceInvitation.Status.EXPIRED);
            invitationRepository.save(invitation);
            throw new ApiException(ErrorCode.INVITE_EXPIRED, "This invitation has expired.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        Shop workspace = shopRepository.findById(invitation.getWorkspaceId()).orElseThrow();
        // Re-checked here, not just at invite time: an invitation is valid for a
        // week, and the shop's plan can lapse inside that window.
        billingService.requireMemberSeat(workspace.getId());
        workspaceAccessService.activate(user, workspace, invitation.getRole(), invitation.getInvitedBy());
        invitation.setStatus(WorkspaceInvitation.Status.ACCEPTED);
        invitation.setAcceptedBy(userId);
        invitationRepository.save(invitation);
        notificationService.emit(workspace.getId(), userId, "JOIN_REQUEST_APPROVED", user.getEmail(),
                "You joined " + workspace.getName(), "Your invitation was accepted.");
    }

    @Transactional
    public void cancel(UUID workspaceId, UUID invitationId) {
        workspaceAccessService.requireSelected(workspaceId);
        // Matched on both ids: an id alone let a member of one shop cancel an
        // invitation belonging to another shop entirely.
        WorkspaceInvitation invitation = invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Invitation", invitationId));
        invitation.setStatus(WorkspaceInvitation.Status.CANCELLED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(UUID workspaceId) {
        workspaceAccessService.requireSelected(workspaceId);
        return invitationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(invitation -> toResponse(invitation, null))
                .toList();
    }

    private InvitationResponse toResponse(WorkspaceInvitation invitation, String token) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getPhone(),
                invitation.getRole().getCode(), invitation.getStatus().name(), invitation.getExpiresAt(), token);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Only the digest of an invitation token is stored, so a database read cannot be turned into
     * a join. Plain SHA-256 is enough: the token is 192 random bits, so there is nothing to guess.
     * The encoding matches what pending invitations were stored with before sign-in moved to
     * Identity, so those still redeem.
     */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
