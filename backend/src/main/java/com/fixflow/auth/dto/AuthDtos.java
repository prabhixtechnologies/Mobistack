package com.fixflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Schema(description = "Stable per-install id; lets the shop tablet keep its own session")
            String deviceId
    ) {
    }

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(
            @NotBlank String refreshToken,
            String deviceId
    ) {
    }

    @Schema(name = "RegisterShopRequest", description = "Creates a shop and its first OWNER user")
    public record RegisterShopRequest(
            @NotBlank @Size(max = 160) String shopName,
            @NotBlank @Size(max = 160) String ownerName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 32) String phone,
            @Size(max = 120) String city
    ) {
    }

    @Schema(name = "ChangePasswordRequest")
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }

    @Schema(name = "ForgotPasswordRequest")
    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    @Schema(name = "ResetPasswordRequest")
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }

    @Schema(name = "OtpRequest")
    public record OtpRequest(@NotBlank @Size(max = 32) String phone) {
    }

    @Schema(name = "VerifyOtpRequest")
    public record VerifyOtpRequest(
            @NotBlank String phone,
            @NotBlank String code
    ) {
    }

    @Schema(name = "EmailStartRequest")
    public record EmailStartRequest(@NotBlank @Email String email) {
    }

    @Schema(name = "EmailOtpVerifyRequest")
    public record EmailOtpVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank String code
    ) {
    }

    @Schema(name = "MagicLinkConsumeRequest")
    public record MagicLinkConsumeRequest(@NotBlank String token, String deviceId) {
    }

    @Schema(name = "RegisterUserRequest")
    public record RegisterUserRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 32) String phone
    ) {
    }

    @Schema(name = "SsoDevRequest")
    public record SsoDevRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String fullName,
            String provider,
            String deviceId
    ) {
    }

    @Schema(name = "ChannelOtpRequest")
    public record ChannelOtpRequest(
            @NotBlank @Size(max = 32) String phone,
            String channel
    ) {
    }

    @Schema(name = "GoogleCodeRequest")
    public record GoogleCodeRequest(
            @NotBlank String code,
            String redirectUri,
            String deviceId
    ) {
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
            boolean mustChangePassword,
            boolean systemAdmin,
            boolean emailVerified,
            boolean phoneVerified,
            boolean paymentRequired,
            boolean catalogOnly,
            java.util.List<String> features,
            String planCode,
            String planName,
            java.time.Instant periodEnd
    ) {
    }

    @Schema(name = "AuthResponse")
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            AuthenticatedUser user,
            List<WorkspaceCard> workspaces,
            String deviceId
    ) {
        public static AuthResponse of(String accessToken, String refreshToken, long expiresIn,
                                      AuthenticatedUser user, List<WorkspaceCard> workspaces) {
            return of(accessToken, refreshToken, expiresIn, user, workspaces, null);
        }

        public static AuthResponse of(String accessToken, String refreshToken, long expiresIn,
                                      AuthenticatedUser user, List<WorkspaceCard> workspaces, String deviceId) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user,
                    workspaces == null ? List.of() : workspaces, deviceId);
        }
    }
}
