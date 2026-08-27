package com.fixflow.notify;

import com.fixflow.security.Permission;
import com.fixflow.security.SystemRole;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tells the right people in a shop that something happened.
 *
 * <p>Business events belong to a shop, not to one account, so an alert about
 * stock or a finished repair has to reach whoever is responsible for it. Every
 * caller previously had to resolve that audience itself, which is why most
 * events were never delivered to anyone.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceNotifier {

    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Alerts every active member whose role carries {@code permission}. Owners
     * and admins always qualify, since they answer for the shop either way.
     *
     * @param link in-app path the alert opens, or null to land on the inbox
     */
    @Transactional
    public void broadcast(UUID workspaceId, Permission permission, String eventType,
                          String subject, String body, String link) {
        if (workspaceId == null) {
            return;
        }
        for (UUID userId : audience(workspaceId, permission)) {
            userRepository.findById(userId).ifPresent(target ->
                    notificationService.emit(workspaceId, target.getId(), eventType,
                            target.getEmail(), subject, body, link));
        }
    }

    /** Alerts one person, looking their address up so callers need only the id. */
    @Transactional
    public void toUser(UUID workspaceId, UUID userId, String eventType, String subject, String body, String link) {
        if (userId == null) {
            return;
        }
        User target = userRepository.findById(userId).orElse(null);
        notificationService.emit(workspaceId, userId, eventType,
                target == null ? null : target.getEmail(), subject, body, link);
    }

    private List<UUID> audience(UUID workspaceId, Permission permission) {
        return membershipRepository.findAllByWorkspaceIdAndStatus(workspaceId, MembershipStatus.ACTIVE).stream()
                .filter(membership -> holds(membership, permission))
                .map(WorkspaceMembership::getUserId)
                .distinct()
                .toList();
    }

    private static boolean holds(WorkspaceMembership membership, Permission permission) {
        if (membership.getRole() == null) {
            return false;
        }
        String code = membership.getRole().getCode();
        if (SystemRole.OWNER.name().equals(code) || SystemRole.ADMIN.name().equals(code)) {
            return true;
        }
        return permission == null || membership.getRole().permissionCodes().contains(permission);
    }
}
