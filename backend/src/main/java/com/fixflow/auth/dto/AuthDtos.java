package com.fixflow.auth.dto;

import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What this API says about the signed-in person.
 *
 * <p>There is no sign-in payload here any more. Credentials, sessions and passwords are Prabhix
 * Identity's; a client arrives with an Identity access token and asks who it is in MobiStack terms:
 * which shop is selected, what it may do there, and what the plan allows.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(name = "AuthenticatedUser")
    public record AuthenticatedUser(
            UUID id,
            UUID shopId,
            String shopName,
            UUID workspaceId,
            String workspaceName,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            Set<String> roles,
            Set<String> permissions,
            boolean systemAdmin,
            boolean emailVerified,
            boolean phoneVerified,
            boolean paymentRequired,
            boolean localActivationAvailable,
            boolean catalogOnly,
            List<String> features,
            String planCode,
            String planName,
            Instant periodEnd,
            boolean commonsReviewer
    ) {
    }

    /**
     * The result of selecting or creating a workspace. The bearer token is unchanged: it names the
     * person, not the shop, so switching shops only changes what this database says about them.
     */
    @Schema(name = "WorkspaceSession")
    public record WorkspaceSession(
            AuthenticatedUser user,
            List<WorkspaceCard> workspaces
    ) {
    }
}
