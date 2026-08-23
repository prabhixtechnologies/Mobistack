package com.fixflow.workspace.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.notify.NotificationService;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.SystemRole;
import com.fixflow.shop.domain.Shop;
import com.fixflow.security.jwt.JwtService;
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
    private final JwtService jwtService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.fixflow.billing.service.BillingService billingService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InvitationResponse invite(UUID workspaceId, InviteMemberRequest request) {
        workspaceAccessService.requireActive(CurrentUser.userId(), workspaceId);
        billingService.require(workspaceId, "MEMBER_ADD");
        Role role = roleRepository.findSystemRoleByCode(request.role().trim().toUpperCase())
                .orElseThrow(() -> ApiException.businessRule("Unknown role " + request.role()));
        String token = generateToken();
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setWorkspaceId(workspaceId);
        invitation.setEmail(request.email());
        invitation.setPhone(request.phone());
        invitation.setRole(role);
        invitation.setTokenHash(jwtService.hashRefreshToken(token));
        invitation.setRawHint(token.substring(0, 6));
        invitation.setStatus(WorkspaceInvitation.Status.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setInvitedBy(CurrentUser.userId());
        invitationRepository.save(invitation);

        Shop workspace = shopRepository.findById(workspaceId).orElseThrow();
        notificationService.emit(workspaceId, CurrentUser.userId(), "USER_INVITED", request.email(),
                "You have been invited to " + workspace.getName(),
                "Join %s as %s. Token: %s (expires in 7 days)."
                        .formatted(workspace.getName(), role.getCode(), token));
        auditService.record(AuditAction.MEMBER_INVITED, "WorkspaceInvitation", invitation.getId(),
                "Invited %s as %s".formatted(request.email(), role.getCode()));
        return toResponse(invitation, token);
    }

    @Transactional
    public void accept(String rawToken, UUID userId) {
        WorkspaceInvitation invitation = invitationRepository.findByTokenHash(jwtService.hashRefreshToken(rawToken))
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
        workspaceAccessService.activate(user, workspace, invitation.getRole(), invitation.getInvitedBy());
        invitation.setStatus(WorkspaceInvitation.Status.ACCEPTED);
        invitation.setAcceptedBy(userId);
        invitationRepository.save(invitation);
        notificationService.emit(workspace.getId(), userId, "JOIN_REQUEST_APPROVED", user.getEmail(),
                "You joined " + workspace.getName(), "Your invitation was accepted.");
    }

    @Transactional
    public void cancel(UUID workspaceId, UUID invitationId) {
        workspaceAccessService.requireActive(CurrentUser.userId(), workspaceId);
        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation", invitationId));
        invitation.setStatus(WorkspaceInvitation.Status.CANCELLED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(UUID workspaceId) {
        workspaceAccessService.requireActive(CurrentUser.userId(), workspaceId);
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

}
