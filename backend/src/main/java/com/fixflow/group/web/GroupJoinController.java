package com.fixflow.group.web;

import com.fixflow.group.service.GroupJoinService;
import com.fixflow.group.service.GroupJoinService.JoinRequestCard;
import com.fixflow.group.service.GroupJoinService.JoinState;
import com.fixflow.security.CurrentUser;
import com.fixflow.workspace.dto.WorkspaceDtos.CompleteJoinRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse;
import com.fixflow.workspace.dto.WorkspaceDtos.JoinWorkspaceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupJoinController {

    private final GroupJoinService joins;

    @GetMapping("/join")
    public JoinState mine() {
        return joins.mine(CurrentUser.userId());
    }

    @PostMapping("/join/checkout")
    public JoinCheckoutResponse checkout(@Valid @RequestBody JoinWorkspaceRequest request) {
        return joins.checkout(CurrentUser.userId(), request.joinCode());
    }

    @PostMapping("/join/complete")
    public JoinState complete(@Valid @RequestBody CompleteJoinRequest request) {
        return joins.complete(CurrentUser.userId(), request);
    }

    @PostMapping("/join/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel() {
        joins.cancel(CurrentUser.userId());
    }

    @GetMapping("/{id}/requests")
    public List<JoinRequestCard> pending(@PathVariable UUID id) {
        return joins.pending(id);
    }

    @PostMapping("/{id}/requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID id, @PathVariable UUID requestId) {
        joins.approve(id, requestId);
    }

    @PostMapping("/{id}/requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @PathVariable UUID requestId) {
        joins.reject(id, requestId);
    }
}
