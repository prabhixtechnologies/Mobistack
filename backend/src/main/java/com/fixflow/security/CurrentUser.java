package com.fixflow.security;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Single accessor for "who is calling and which shop are they in".
 *
 * <p>Services take the shop id from here rather than from a request parameter,
 * so a caller cannot reach another tenant's data by editing a payload.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static UserPrincipal require() {
        return find().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required."));
    }

    public static UUID shopId() {
        return workspaceId();
    }

    /** Selected workspace. Never taken from a request body. */
    public static UUID workspaceId() {
        UUID workspaceId = require().getShopId();
        if (workspaceId == null) {
            throw new ApiException(ErrorCode.WORKSPACE_REQUIRED,
                    "Select a workspace before calling this endpoint.");
        }
        return workspaceId;
    }

    public static UUID userId() {
        return require().getId();
    }

    public static boolean has(Permission permission) {
        return find().map(p -> p.has(permission)).orElse(false);
    }
}
