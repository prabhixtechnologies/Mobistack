package com.fixflow.workspace.web;

import com.fixflow.security.CurrentUser;
import com.fixflow.workspace.service.InvitationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public record AcceptInviteRequest(@NotBlank String token) {
    }

    @PostMapping("/accept")
    public void accept(@RequestBody AcceptInviteRequest request) {
        invitationService.accept(request.token(), CurrentUser.userId());
    }
}
