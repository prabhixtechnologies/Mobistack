package com.fixflow.workspace.web;

import com.fixflow.auth.dto.AuthDtos.WorkspaceSession;
import com.fixflow.auth.service.AuthService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import com.fixflow.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.DecideMembershipRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.InvitationResponse;
import com.fixflow.workspace.dto.WorkspaceDtos.InviteMemberRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.JoinWorkspaceRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.MyWorkspacesResponse;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceCard;
import com.fixflow.workspace.dto.WorkspaceDtos.WorkspaceMember;
import com.fixflow.workspace.service.InvitationService;
import com.fixflow.workspace.service.WorkspaceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces")
public class WorkspaceController {

    private final WorkspaceAccessService workspaceAccessService;
    private final InvitationService invitationService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Workspaces this account can see, including pending join requests")
    public MyWorkspacesResponse mine() {
        return workspaceAccessService.listMine(CurrentUser.userId(),
                CurrentUser.find().map(principal -> principal.getShopId()).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workspace and select it; the bearer token stays the same")
    public WorkspaceSession create(@Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceCard card = workspaceAccessService.create(CurrentUser.userId(), request);
        return authService.switchWorkspace(CurrentUser.userId(), card.id());
    }

    @PostMapping("/join/checkout")
    @Operation(summary = "Start the ₹50 join payment for one shop. Each shop is a separate payment.")
    public com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse joinCheckout(
            @Valid @RequestBody JoinWorkspaceRequest request) {
        return workspaceAccessService.beginPaidJoin(CurrentUser.userId(), request.joinCode());
    }

    @PostMapping("/join/complete")
    @Operation(summary = "Verify the join payment and file the request. Stays PENDING until an owner approves.")
    public WorkspaceCard joinComplete(
            @Valid @RequestBody com.fixflow.workspace.dto.WorkspaceDtos.CompleteJoinRequest request) {
        return workspaceAccessService.completePaidJoin(CurrentUser.userId(), request);
    }

    @PostMapping("/join")
    @Operation(summary = "Request to join by code after the join fee is paid. Stays PENDING until an owner approves.")
    public WorkspaceCard join(@Valid @RequestBody JoinWorkspaceRequest request) {
        return workspaceAccessService.requestJoin(CurrentUser.userId(), request.joinCode());
    }

    @PostMapping("/{id}/select")
    @Operation(summary = "Switch the selected workspace; the bearer token stays the same")
    public WorkspaceSession select(@PathVariable UUID id) {
        return authService.switchWorkspace(CurrentUser.userId(), id);
    }

    @PostMapping("/{id}/join/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw your own pending join request. The ₹50 stays on this shop.")
    public void cancelJoin(@PathVariable UUID id) {
        workspaceAccessService.cancelJoinRequest(CurrentUser.userId(), id);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize(Authorize.USER_READ)
    @Operation(summary = "Members of a workspace, including pending join requests")
    public PageResponse<WorkspaceMember> members(@PathVariable UUID id,
                                                 @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(workspaceAccessService.listMembers(id, pageable));
    }

    @PostMapping("/{id}/members/{membershipId}/approve")
    @PreAuthorize(Authorize.USER_WRITE)
    public WorkspaceMember approve(@PathVariable UUID id, @PathVariable UUID membershipId,
                                   @RequestBody(required = false) DecideMembershipRequest request) {
        return workspaceAccessService.approve(id, membershipId, request == null ? null : request.role());
    }

    @PostMapping("/{id}/members/{membershipId}/reject")
    @PreAuthorize(Authorize.USER_WRITE)
    public WorkspaceMember reject(@PathVariable UUID id, @PathVariable UUID membershipId) {
        return workspaceAccessService.reject(id, membershipId);
    }

    @PostMapping("/{id}/members/{membershipId}/suspend")
    @PreAuthorize(Authorize.USER_WRITE)
    public WorkspaceMember suspend(@PathVariable UUID id, @PathVariable UUID membershipId) {
        return workspaceAccessService.suspend(id, membershipId);
    }

    @PostMapping("/{id}/members/{membershipId}/remove")
    @PreAuthorize(Authorize.USER_WRITE)
    public WorkspaceMember remove(@PathVariable UUID id, @PathVariable UUID membershipId) {
        return workspaceAccessService.remove(id, membershipId);
    }

    @PostMapping("/{id}/members/{membershipId}/purge")
    @PreAuthorize(Authorize.USER_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a removed or rejected membership from this shop. Does not delete their account.")
    public void purge(@PathVariable UUID id, @PathVariable UUID membershipId) {
        workspaceAccessService.purge(id, membershipId);
    }

    @GetMapping("/{id}/invitations")
    @PreAuthorize(Authorize.USER_INVITE)
    public java.util.List<InvitationResponse> invitations(@PathVariable UUID id) {
        return invitationService.list(id);
    }

    @PostMapping("/{id}/invitations")
    @PreAuthorize(Authorize.USER_INVITE)
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@PathVariable UUID id, @Valid @RequestBody InviteMemberRequest request) {
        return invitationService.invite(id, request);
    }

    @PostMapping("/{id}/invitations/{invitationId}/cancel")
    @PreAuthorize(Authorize.USER_INVITE)
    public void cancelInvite(@PathVariable UUID id, @PathVariable UUID invitationId) {
        invitationService.cancel(id, invitationId);
    }

    @GetMapping("/{id}/join-qr")
    public java.util.Map<String, String> joinQr(@PathVariable UUID id) {
        workspaceAccessService.requireActive(CurrentUser.userId(), id);
        var mine = workspaceAccessService.listMine(CurrentUser.userId(), id);
        var card = mine.workspaces().stream().filter(w -> w.id().equals(id)).findFirst()
                .orElseThrow(() -> com.fixflow.common.error.ApiException.notFound("Workspace", id));
        String code = card.joinCode() == null ? "" : card.joinCode();
        return java.util.Map.of("joinCode", code, "payload", "fixflow://join/" + code);
    }

}
