package com.fixflow.workspace.dto;

import com.fixflow.workspace.domain.MembershipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    @Schema(name = "CreateWorkspaceRequest")
    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String shopName,
            @Size(max = 160) String ownerName,
            @Size(max = 32) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 120) String city
    ) {
    }

    @Schema(name = "JoinWorkspaceRequest")
    public record JoinWorkspaceRequest(
            @NotBlank @Size(max = 16) String joinCode
    ) {
    }

    @Schema(name = "JoinCheckoutResponse")
    public record JoinCheckoutResponse(
            UUID id,
            @com.fasterxml.jackson.annotation.JsonProperty("order_id") String orderId,
            long amount,
            String currency,
            String keyId,
            String priceCode,
            String gateway,
            String shopName,
            boolean alreadyPaid
    ) {
        public static JoinCheckoutResponse alreadyPaid(String shopName) {
            return new JoinCheckoutResponse(null, null, 0, "INR", null, "WORKSPACE_JOIN", "PAID", shopName, true);
        }
    }

    @Schema(name = "CompleteJoinRequest")
    public record CompleteJoinRequest(
            @NotBlank @Size(max = 16) String joinCode,
            @com.fasterxml.jackson.annotation.JsonAlias({"razorpay_order_id", "order_id"}) String razorpayOrderId,
            @com.fasterxml.jackson.annotation.JsonAlias({"razorpay_payment_id", "payment_id"}) String razorpayPaymentId,
            @com.fasterxml.jackson.annotation.JsonAlias({"razorpay_signature", "signature"}) String razorpaySignature,
            UUID orderId
    ) {
    }

    @Schema(name = "WorkspaceCard", description = "One tile on the My Workspaces screen")
    public record WorkspaceCard(
            UUID id,
            String name,
            String city,
            String logoUrl,
            String joinCode,
            String role,
            MembershipStatus status,
            long memberCount,
            long productCount,
            boolean selected
    ) {
    }

    @Schema(name = "WorkspaceMember")
    public record WorkspaceMember(
            UUID membershipId,
            UUID userId,
            String fullName,
            String email,
            String role,
            MembershipStatus status,
            Instant joinedAt
    ) {
    }

    @Schema(name = "MyWorkspacesResponse")
    public record MyWorkspacesResponse(
            UUID selectedWorkspaceId,
            List<WorkspaceCard> workspaces
    ) {
    }

    @Schema(name = "InviteMemberRequest")
    public record InviteMemberRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @NotBlank String role
    ) {
    }

    @Schema(name = "InvitationResponse")
    public record InvitationResponse(
            UUID id,
            String email,
            String phone,
            String role,
            String status,
            Instant expiresAt,
            String token
    ) {
    }

    @Schema(name = "DecideMembershipRequest")
    public record DecideMembershipRequest(String role) {
    }
}
