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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/groups")
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

    @GetMapping("/requests")
    public List<JoinRequestCard> pending(@RequestParam UUID id) {
        return joins.pending(id);
    }

    @PostMapping("/requests/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@RequestParam UUID id, @RequestParam UUID requestId) {
        joins.approve(id, requestId);
    }

    @PostMapping("/requests/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@RequestParam UUID id, @RequestParam UUID requestId) {
        joins.reject(id, requestId);
    }
}
